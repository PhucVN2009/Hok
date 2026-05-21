#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include "Includes/obfuscate.h"
#include "Includes/Logger.h"
#include "Includes/Macros.h"
#include "Includes/JNIStuff.h"
#include "Includes/Utils.h"
#include "STARCOOLX/Call_Me.h"
#include "STARCOOLX/IL2CppSDKGenerator/Il2Cpp.h"
#include "STARCOOLX/IL2CppSDKGenerator/Unity.h"
#include "STARCOOL.h"
#include "SocketControl.h"

// ========== TOGGLE FLAGS ==========
bool  hackMap     = false;
bool  hackCamXa   = false;
float camXaValue  = 0.0f;
bool  hackEsp     = false;

// ========== ESP SHARED STATE ==========
ESPHero g_espHeroes[ESP_MAX_HEROES];
int     g_espHeroCount = 0;
int     g_selfCamp     = 0;
float   g_screenW      = 1080.0f;
float   g_screenH      = 2400.0f;

// ========== OFFSETS (dump 64BIT 1.62.1.4) ==========
// KyriosFramework (MonoSingleton)
#define OFF_KYRIOS_ACTORMGR     0x28   // ActorManager*

// ActorManager
#define OFF_MGR_HEROACTORS      0x20   // List<PoolObjHandle<ActorLinker>>

// IL2CPP List<T>
#define OFF_LIST_ITEMS          0x10   // Array*
#define OFF_LIST_SIZE           0x18   // int

// IL2CPP Array data offset
#define OFF_ARRAY_DATA          0x20

// PoolObjHandle<ActorLinker>: stride=16, _handleObj @ +0x8
#define POOL_STRIDE             16
#define OFF_HANDLE_OBJ          0x8

// ActorLinker
#define OFF_ACTOR_LOC_X         0x164  // VInt3.x (int, /1000 = metres)
#define OFF_ACTOR_LOC_Y         0x168  // VInt3.y
#define OFF_ACTOR_LOC_Z         0x16C  // VInt3.z
#define OFF_ACTOR_VALUE_COMP    0x28   // ValueLinkerComponent*
#define OFF_ACTOR_CHARINFO      0x110  // CActorInfo*
#define OFF_ACTOR_TYPE          0x200  // ActorTypeDef (int)
#define OFF_ACTOR_CAMP          0x204  // COM_PLAYERCAMP (int)
#define OFF_ACTOR_TRANSFORM     0x430  // Transform*
#define OFF_ACTOR_HOSTCTRL      0x190  // bool m_bIsHostCtrlActor

// ValueLinkerComponent
#define OFF_VALUE_HP            0x40   // int actorHp
#define OFF_VALUE_MAXHP         0x44   // int actorHpTotal

// CActorInfo
#define OFF_CHARINFO_NAME       0x10   // string* ActorName

// Il2CppString
#define OFF_STR_LEN             0x10   // int32 length
#define OFF_STR_CHARS           0x14   // uint16[] chars (UTF-16)

// CameraSystem
#define OFF_CAMSYS_MAINCAM      0x108  // UnityEngine.Camera*

// ActorTypeDef: Hero = 0
#define ACTOR_TYPE_HERO         0

// ========== CAMERA / W2S ==========
struct Vec3 { float x, y, z; };

typedef Vec3 (*fn_W2S_t)(void* camera, Vec3 pos, int eye);
static fn_W2S_t fn_W2S = nullptr;

static void* g_cameraSystem = nullptr; // captured from GetCameraHeightRateValue hook

static Vec3 DoW2S(void* cam, Vec3 worldPos) {
    if (fn_W2S && cam)
        return fn_W2S(cam, worldPos, 0);
    // Fallback: approximate top-down MOBA projection
    Vec3 s;
    s.x = worldPos.x * (g_screenW / 140.0f) + g_screenW * 0.5f;
    s.y = g_screenH - (worldPos.z * (g_screenH / 140.0f) + g_screenH * 0.5f);
    s.z = 1.0f;
    return s;
}

// ========== HELPERS ==========
template<typename T>
static inline T Rd(uintptr_t addr) {
    if (addr < 0x1000) return T{};
    return *reinterpret_cast<T*>(addr);
}

static void ReadName(void* strPtr, char* out, int maxLen) {
    out[0] = '\0';
    if (!strPtr) return;
    uintptr_t b = (uintptr_t)strPtr;
    int len = Rd<int32_t>(b + OFF_STR_LEN);
    if (len <= 0 || len > 64) return;
    const uint16_t* chars = reinterpret_cast<const uint16_t*>(b + OFF_STR_CHARS);
    int pos = 0;
    for (int i = 0; i < len && pos < maxLen - 1; i++) {
        uint16_t c = chars[i];
        if (c < 0x80) {
            out[pos++] = (char)c;
        } else if (c < 0x800) {
            if (pos + 1 >= maxLen - 1) break;
            out[pos++] = (char)(0xC0 | (c >> 6));
            out[pos++] = (char)(0x80 | (c & 0x3F));
        } else {
            if (pos + 2 >= maxLen - 1) break;
            out[pos++] = (char)(0xE0 | (c >> 12));
            out[pos++] = (char)(0x80 | ((c >> 6) & 0x3F));
            out[pos++] = (char)(0x80 | (c & 0x3F));
        }
    }
    out[pos] = '\0';
}

// ========== UPDATE ESP DATA ==========
static void UpdateESPData() {
    // 1. KyriosFramework instance
    void* kyriosInst = nullptr;
    IL2Cpp::Il2CppGetStaticFieldValue(
        "Assembly-CSharp.dll", "Kyrios", "KyriosFramework", "_instance", &kyriosInst);
    if (!kyriosInst) return;

    // 2. ActorManager
    void* actorMgr = Rd<void*>((uintptr_t)kyriosInst + OFF_KYRIOS_ACTORMGR);
    if (!actorMgr) return;

    // 3. HeroActors list
    void* heroList = Rd<void*>((uintptr_t)actorMgr + OFF_MGR_HEROACTORS);
    if (!heroList) return;

    int heroCount = Rd<int>((uintptr_t)heroList + OFF_LIST_SIZE);
    if (heroCount <= 0 || heroCount > ESP_MAX_HEROES * 2) return;

    void* itemsArr = Rd<void*>((uintptr_t)heroList + OFF_LIST_ITEMS);
    if (!itemsArr) return;

    // 4. Camera
    void* mainCam = nullptr;
    if (g_cameraSystem)
        mainCam = Rd<void*>((uintptr_t)g_cameraSystem + OFF_CAMSYS_MAINCAM);

    // 5. Iterate heroes
    pthread_mutex_lock(&g_espMutex);

    int count = 0;
    int selfCampTmp = 0;
    uintptr_t data = (uintptr_t)itemsArr + OFF_ARRAY_DATA;

    for (int i = 0; i < heroCount && count < ESP_MAX_HEROES; i++) {
        uintptr_t handleBase = data + (uintptr_t)i * POOL_STRIDE;
        void* actorPtr = Rd<void*>(handleBase + OFF_HANDLE_OBJ);
        if (!actorPtr) continue;

        uintptr_t a = (uintptr_t)actorPtr;

        // Hero only
        if (Rd<int>(a + OFF_ACTOR_TYPE) != ACTOR_TYPE_HERO) continue;

        int camp = Rd<int>(a + OFF_ACTOR_CAMP);
        if (camp <= 0 || camp > 2) continue;

        // Detect local player
        if (Rd<bool>(a + OFF_ACTOR_HOSTCTRL) && selfCampTmp == 0)
            selfCampTmp = camp;

        // HP
        void* vc = Rd<void*>(a + OFF_ACTOR_VALUE_COMP);
        int hp    = vc ? Rd<int>((uintptr_t)vc + OFF_VALUE_HP)    : 0;
        int maxHp = vc ? Rd<int>((uintptr_t)vc + OFF_VALUE_MAXHP) : 1;
        if (hp <= 0 || maxHp <= 0) continue;

        // World position
        Vec3 wp;
        wp.x = Rd<int>(a + OFF_ACTOR_LOC_X) / 1000.0f;
        wp.y = Rd<int>(a + OFF_ACTOR_LOC_Y) / 1000.0f;
        wp.z = Rd<int>(a + OFF_ACTOR_LOC_Z) / 1000.0f;

        // W2S
        Vec3 sp = DoW2S(mainCam, wp);
        if (sp.z < 0) continue;

        // Name
        char name[64] = "Hero";
        void* ci = Rd<void*>(a + OFF_ACTOR_CHARINFO);
        if (ci) {
            void* ns = Rd<void*>((uintptr_t)ci + OFF_CHARINFO_NAME);
            ReadName(ns, name, 64);
        }

        ESPHero& h = g_espHeroes[count++];
        h.screenX = sp.x;
        h.screenY = sp.y; // already flipped in DoW2S
        h.worldX  = wp.x;
        h.worldZ  = wp.z;
        h.hp      = hp;
        h.maxHp   = maxHp;
        h.camp    = camp;
        h.isEnemy = false; // set after loop
        h.valid   = true;
        strncpy(h.name, name, 63);
        h.name[63] = '\0';
    }

    if (selfCampTmp) g_selfCamp = selfCampTmp;
    for (int i = 0; i < count; i++)
        g_espHeroes[i].isEnemy = (g_selfCamp != 0)
            ? (g_espHeroes[i].camp != g_selfCamp)
            : (g_espHeroes[i].camp == 2);

    g_espHeroCount = count;
    pthread_mutex_unlock(&g_espMutex);
}

// ========== MAP HACK (SetVisible) ==========
enum COM_PLAYERCAMP {
    ComPlayercampMid = 0,
    ComPlayercamp1   = 1,
    ComPlayercamp2   = 2
};

void (*_SetVisible)(void* instance, COM_PLAYERCAMP camp, bool bVisible, bool forceSync);
void SetVisible(void* instance, COM_PLAYERCAMP camp, bool bVisible, bool forceSync) {
    if (instance != NULL && hackMap) {
        if (camp == ComPlayercamp1 || camp == ComPlayercamp2)
            bVisible = true;
    }
    _SetVisible(instance, camp, bVisible, forceSync);
}

// ========== CAM XA HOOK ==========
// RVA: 0x8D546F4
float (*old_GetCameraHeightRateValue)(void* instance, int type);
float GetCameraHeightRateValue(void* instance, int type) {
    float original = old_GetCameraHeightRateValue(instance, type);
    // Cache CameraSystem instance cho ESP W2S
    if (instance && !g_cameraSystem)
        g_cameraSystem = instance;
    if (instance && hackCamXa)
        return original + camXaValue;
    return original;
}

// ========== THREAD CHÍNH ==========
void* Init_Thread(void*) {
    uintptr_t il2cppMap = 0;
    for (int i = 0; i < 15; i++) {
        il2cppMap = Tools::GetBaseAddress("libil2cpp.so");
        if (il2cppMap != 0) break;
        sleep(2);
    }
    if (il2cppMap == 0) return nullptr;

    // Hook Map Hack (SetVisible)
    DobbyHook((void*)(il2cppMap + 0x6BCE384),
              (void*)SetVisible, (void**)&_SetVisible);

    // Hook Cam Xa (GetCameraHeightRateValue)
    DobbyHook((void*)(il2cppMap + 0x8D546F4),
              (void*)GetCameraHeightRateValue,
              (void**)&old_GetCameraHeightRateValue);

    // Resolve Camera.WorldToScreenPoint
    IL2Cpp::Il2CppAttach();
    fn_W2S = (fn_W2S_t)IL2Cpp::Il2CppGetMethodOffset(
        "UnityEngine.CoreModule.dll", "UnityEngine", "Camera",
        "WorldToScreenPoint", 1);
    LOGD("[ESP] fn_W2S=%p", (void*)fn_W2S);

    // Main loop — giống gốc nhưng thêm ESP update
    while (true) {
        if (hackEsp)
            UpdateESPData();
        usleep(50000); // 50ms
    }
    return nullptr;
}

// ========== LIBRARY ENTRY ==========
__attribute__((constructor))
void lib_main() {
    pthread_t ptid;
    pthread_create(&ptid, NULL, socket_server_thread, NULL);
    pthread_t myThread;
    pthread_create(&myThread, NULL, Init_Thread, NULL);
}

// ========== JNI ==========
extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    return JNI_VERSION_1_6;
}
