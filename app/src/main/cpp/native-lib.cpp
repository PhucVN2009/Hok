#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include "Includes/obfuscate.h"
#include "Includes/Logger.h"
#include "Includes/Macros.h"
#include "Includes/JNIStuff.h"
#include "Includes/Utils.h"
#include "STARCOOLX/Call_Me.h"
#include "STARCOOL.h"
#include "SocketControl.h"

// ========== TOGGLE FLAGS ==========
bool hackMap    = false;
bool hackCamXa  = false;
float camXaValue= 0.0f;
bool hackSkin   = false;
bool hackESP    = false;

// ========== ENUM CAMP ==========
enum COM_PLAYERCAMP {
    ComPlayercampMid = 0,
    ComPlayercamp1   = 1,
    ComPlayercamp2   = 2
};

// ================================================================
// MAP HACK — SetVisible  RVA: 0x6BCE384
// void SetVisible(instance, camp, bVisible, forceSync)
// ================================================================
void (*_SetVisible)(void*, COM_PLAYERCAMP, bool, bool);
void SetVisible(void *instance, COM_PLAYERCAMP camp, bool bVisible, bool forceSync) {
    if (instance != NULL && hackMap) {
        if (camp == ComPlayercamp1 || camp == ComPlayercamp2) bVisible = true;
    }
    _SetVisible(instance, camp, bVisible, forceSync);
}

// ================================================================
// CAM XA — GetCameraHeightRateValue  RVA: 0x8D546F4
// float GetCameraHeightRateValue(instance, type)
// ================================================================
float (*old_GetCameraHeightRateValue)(void*, int);
float GetCameraHeightRateValue(void *instance, int type) {
    float orig = old_GetCameraHeightRateValue(instance, type);
    if (instance != NULL && hackCamXa) return orig + camXaValue;
    return orig;
}

// ================================================================
// SKIN UNLOCK (lobby) — IsCanUseSkin + IsHaveHeroSkin
// RVA: 0x7E08630 / 0x7E079AC
// ================================================================
bool (*old_IsCanUseSkin)(void*, uint32_t, uint32_t);
bool IsCanUseSkin(void *self, uint32_t heroId, uint32_t skinId) {
    if (hackSkin) return true;
    return old_IsCanUseSkin(self, heroId, skinId);
}

bool (*old_IsHaveHeroSkin)(void*, uint32_t, uint32_t, bool);
bool IsHaveHeroSkin(void *self, uint32_t heroId, uint32_t skinId, bool incTime) {
    if (hackSkin) return true;
    return old_IsHaveHeroSkin(self, heroId, skinId, incTime);
}

// ================================================================
// ESP — ActorLinker tracking
// ActorLinker.Update  RVA: 0x8A4F118
// ActorLinker.IsHostPlayer  RVA: 0x8A52478
// ActorLinker.get_objType   RVA: 0x8A49680  (0=hero)
// ActorLinker.get_objCamp   RVA: 0x8A49630
// ActorLinker.get_position  RVA: 0x8A490AC
// ================================================================
static bool    (*fp_IsHostPlayer)(void*) = nullptr;
static int     (*fp_get_objType)(void*)  = nullptr;
static int     (*fp_get_objCamp)(void*)  = nullptr;
static Vector3 (*fp_get_position)(void*) = nullptr;
static void*   (*fp_Camera_main)()       = nullptr; // RVA: 0x94AE0DC
static Vector2 (*fp_W2S)(void*, Vector3) = nullptr; // RVA: 0x71C99BC
static int     (*fp_getHp)(void*)        = nullptr; // RVA: 0x8A7B378
static int     (*fp_getHpMax)(void*)     = nullptr; // RVA: 0x8A7B388

static void *g_myPlayer = nullptr;

struct EspEntry { void *al; };
static std::vector<EspEntry> g_enemies;
static pthread_mutex_t g_lock = PTHREAD_MUTEX_INITIALIZER;

void (*old_ActorLinker_Update)(void*);
void ActorLinker_Update(void *instance) {
    old_ActorLinker_Update(instance);
    if (!instance || !hackESP) return;
    if (!fp_IsHostPlayer || !fp_get_objType) return;
    if (fp_get_objType(instance) != 0) return; // 0 = hero

    pthread_mutex_lock(&g_lock);
    if (fp_IsHostPlayer(instance)) {
        g_myPlayer = instance;
    } else {
        for (auto &e : g_enemies) if (e.al == instance) { pthread_mutex_unlock(&g_lock); return; }
        if (g_enemies.size() < 10) g_enemies.push_back({instance});
    }
    pthread_mutex_unlock(&g_lock);
}

// ================================================================
// JNI: GetEspActors — called by EspOverlayView.onDraw every frame
// Returns float[] = [count, camp,sx,sy,hp,maxhp, camp,sx,sy,hp,maxhp, ...]
// ================================================================
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_star_android_service_FloatingService_GetEspActors(
        JNIEnv *env, jobject, jint W, jint H) {
    float buf[1 + 10*5];
    buf[0] = 0;
    int cnt = 0;

    if (!hackESP || !fp_Camera_main || !fp_W2S || !fp_get_position) goto done;
    {
        void *cam = fp_Camera_main();
        if (!cam) goto done;
        pthread_mutex_lock(&g_lock);
        for (auto &e : g_enemies) {
            if (!e.al) continue;
            Vector3 pos = fp_get_position(e.al);
            Vector2 sp  = fp_W2S(cam, pos);
            float sx = sp.x, sy = (float)H - sp.y;
            if (sx < -300 || sx > W+300 || sy < -300 || sy > H+300) continue;

            int camp = fp_get_objCamp ? fp_get_objCamp(e.al) : 0;
            // HP từ ValueLinkerComponent tại offset 0x28 trong ActorLinker
            int hp = 0, mhp = 100;
            void *vc = *(void**)((uintptr_t)e.al + 0x28);
            if (vc && fp_getHp && fp_getHpMax) {
                hp  = fp_getHp(vc);
                mhp = fp_getHpMax(vc);
                if (mhp <= 0) mhp = 100;
            }
            int i = 1 + cnt*5;
            buf[i]   = (float)camp;
            buf[i+1] = sx;   buf[i+2] = sy;
            buf[i+3] = (float)hp; buf[i+4] = (float)mhp;
            if (++cnt >= 10) break;
        }
        pthread_mutex_unlock(&g_lock);
    }
done:
    buf[0] = (float)cnt;
    jfloatArray arr = env->NewFloatArray(1 + cnt*5);
    if (arr) env->SetFloatArrayRegion(arr, 0, 1 + cnt*5, buf);
    return arr;
}

// ================================================================
// THREAD CHÍNH — giống hệt source gốc
// ================================================================
void *Init_Thread(void *) {
    uintptr_t base = 0;
    for (int i = 0; i < 15; i++) {
        base = Tools::GetBaseAddress("libil2cpp.so");
        if (base != 0) break;
        sleep(2);
    }
    if (base == 0) return nullptr;

    // Map Hack
    DobbyHook((void*)(base + 0x6BCE384), (void*)SetVisible, (void**)&_SetVisible);

    // Cam Xa
    DobbyHook((void*)(base + 0x8D546F4), (void*)GetCameraHeightRateValue,
              (void**)&old_GetCameraHeightRateValue);

    // Skin unlock
    DobbyHook((void*)(base + 0x7E08630), (void*)IsCanUseSkin,   (void**)&old_IsCanUseSkin);
    DobbyHook((void*)(base + 0x7E079AC), (void*)IsHaveHeroSkin, (void**)&old_IsHaveHeroSkin);

    // ESP
    DobbyHook((void*)(base + 0x8A4F118), (void*)ActorLinker_Update, (void**)&old_ActorLinker_Update);

    fp_IsHostPlayer = (bool(*)(void*))            (base + 0x8A52478);
    fp_get_objType  = (int(*)(void*))             (base + 0x8A49680);
    fp_get_objCamp  = (int(*)(void*))             (base + 0x8A49630);
    fp_get_position = (Vector3(*)(void*))         (base + 0x8A490AC);
    fp_Camera_main  = (void*(*)())                (base + 0x94AE0DC);
    fp_W2S          = (Vector2(*)(void*, Vector3))(base + 0x71C99BC);
    fp_getHp        = (int(*)(void*))             (base + 0x8A7B378);
    fp_getHpMax     = (int(*)(void*))             (base + 0x8A7B388);

    while (true) sleep(1); // giữ thread (giống gốc)
    return nullptr;
}

// ================================================================
// LIBRARY ENTRY — giống hệt source gốc
// ================================================================
__attribute__((constructor))
void lib_main() {
    pthread_t ptid;
    pthread_create(&ptid, NULL, socket_server_thread, NULL);
    pthread_t myThread;
    pthread_create(&myThread, NULL, Init_Thread, NULL);
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    return JNI_VERSION_1_6;
}
