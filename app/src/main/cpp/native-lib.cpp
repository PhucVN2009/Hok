#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <vector>
#include <cstring>
#include <cstdio>
#include "Includes/obfuscate.h"
#include "Includes/Logger.h"
#include "Includes/Macros.h"
#include "Includes/JNIStuff.h"
#include "Includes/Utils.h"
#include "STARCOOLX/Call_Me.h"
#include "STARCOOL.h"
#include "SocketControl.h"

// ================================================================
// TOGGLE FLAGS
// ================================================================
bool hackMap    = false;   // Map Hack / SetVisible
bool hackCamXa  = false;   // Camera zoom
float camXaValue= 0.0f;
bool hackSkin   = false;   // Mod Skin
bool hackESP    = false;   // ESP overlay

// ================================================================
// MOD SKIN — theo logic modskinqqq.h
// Dump HOK 1.62.1.4:
//   COMDT_HERO_COMMON_INFO.dwHeroID @ 0x8
//   COMDT_HERO_COMMON_INFO.wSkinID  @ 0x3A
//   unpack RVA                      : 0x3A10664
//   CRoleInfo.IsCanUseSkin(u,u)     : 0x7E08630
//   CRoleInfo.IsHaveHeroSkin(u,u,b) : 0x7E079AC
//   CRoleInfo.GetWearSkinId()       : 0x7637D40
//   CRoleInfo.RefreshHeroPanel(b,b,b): 0x77C5650
// ================================================================

// Skin target (set qua socket)
uint32_t g_skinHeroId = 0;
uint16_t g_skinId     = 0;

// saveData: lưu skin gốc để restore khi tắt
struct SkinEntry {
    void*    instance;
    uint16_t origSkinId;
};
static std::vector<SkinEntry> g_skinSave;

namespace CSProtocol {

class COMDT_HERO_COMMON_INFO {
public:
    static uint32_t getDwHeroID(void* p) {
        if (!p) return 0;
        return *(uint32_t*)((uintptr_t)p + 0x8);
    }
    static uint16_t getWSkinID(void* p) {
        if (!p) return 0;
        return *(uint16_t*)((uintptr_t)p + 0x3A);
    }
    static void setWSkinID(void* p, uint16_t v) {
        if (!p) return;
        *(uint16_t*)((uintptr_t)p + 0x3A) = v;
    }
};

} // namespace CSProtocol

// Hook: COMDT_HERO_COMMON_INFO.unpack — intercept skin khi load trận
typedef int32_t TdrErrorType;
TdrErrorType (*_unpack)(CSProtocol::COMDT_HERO_COMMON_INFO* self, void* buf, uint32_t cutVer);
TdrErrorType hook_unpack(CSProtocol::COMDT_HERO_COMMON_INFO* self, void* buf, uint32_t cutVer) {
    TdrErrorType result = _unpack(self, buf, cutVer);
    if (!hackSkin || !self) return result;
    if (g_skinHeroId == 0 || g_skinId == 0) return result;

    if (CSProtocol::COMDT_HERO_COMMON_INFO::getDwHeroID(self) == g_skinHeroId) {
        // Lưu skin gốc để restore
        bool found = false;
        for (auto& e : g_skinSave) {
            if (e.instance == self) { found = true; break; }
        }
        if (!found) {
            g_skinSave.push_back({self, CSProtocol::COMDT_HERO_COMMON_INFO::getWSkinID(self)});
        }
        CSProtocol::COMDT_HERO_COMMON_INFO::setWSkinID(self, g_skinId);
    }
    return result;
}

// Hook: CRoleInfo.IsCanUseSkin(uint heroId, uint skinId)
bool (*_IsCanUseSkin)(void* self, uint32_t heroId, uint32_t skinId);
bool hook_IsCanUseSkin(void* self, uint32_t heroId, uint32_t skinId) {
    if (hackSkin && heroId != 0) {
        // Cập nhật hero target khi user chọn skin
        if (g_skinHeroId == 0) g_skinHeroId = heroId;
        return true;
    }
    return _IsCanUseSkin(self, heroId, skinId);
}

// Hook: CRoleInfo.IsHaveHeroSkin(uint heroId, uint skinId, bool isIncludeTimeLimited)
bool (*_IsHaveHeroSkin)(void* self, uint32_t heroId, uint32_t skinId, bool incTime);
bool hook_IsHaveHeroSkin(void* self, uint32_t heroId, uint32_t skinId, bool incTime) {
    if (hackSkin) return true;
    return _IsHaveHeroSkin(self, heroId, skinId, incTime);
}

// Hook: CRoleInfo.GetWearSkinId() — trả về skin ID khi game cần biết hero mặc skin nào
uint32_t (*_GetWearSkinId)(void* self);
uint32_t hook_GetWearSkinId(void* self) {
    if (hackSkin && g_skinId != 0) return (uint32_t)g_skinId;
    return _GetWearSkinId(self);
}

// ================================================================
// MAP HACK — SetVisible(instance, camp, bVisible, forceSync)
// RVA: 0x6BCE384
// ================================================================
enum COM_PLAYERCAMP {
    ComPlayercampMid = 0,
    ComPlayercamp1   = 1,
    ComPlayercamp2   = 2
};

void (*_SetVisible)(void* instance, COM_PLAYERCAMP camp, bool bVisible, bool forceSync);
void hook_SetVisible(void* instance, COM_PLAYERCAMP camp, bool bVisible, bool forceSync) {
    if (instance && hackMap) {
        if (camp == ComPlayercamp1 || camp == ComPlayercamp2) {
            bVisible = true;
        }
    }
    _SetVisible(instance, camp, bVisible, forceSync);
}

// ================================================================
// CAM XA — GetCameraHeightRateValue
// RVA: 0x8D546F4
// ================================================================
float (*_GetCameraHeightRateValue)(void* instance, int type);
float hook_GetCameraHeightRateValue(void* instance, int type) {
    float orig = _GetCameraHeightRateValue(instance, type);
    if (instance && hackCamXa) return orig + camXaValue;
    return orig;
}

// ================================================================
// ESP — actor positions gửi về Android qua socket
// ActorLinker.get_objCamp() RVA: 0x8A49630
// ActorLinker.get_position() RVA: 0x8A490AC
// WorldToScreenPoint(Camera,Vector3)→Vector2 RVA: 0x71C99BC
// Camera.get_main() RVA: 0x94AE0DC
// ================================================================

// Vector2, Vector3 đã defined trong STARCOOLX/IL2CppSDKGenerator/

COM_PLAYERCAMP (*fp_GetObjCamp)(void* actorLinker);
Vector3        (*fp_GetPosition)(void* actorLinker);
Vector2        (*fp_WorldToScreen)(void* camera, Vector3 worldPoint);
void*          (*fp_GetMainCamera)();

// Actor data gửi qua socket
struct ActorESPData {
    int   camp;   // 1 or 2
    float sx, sy; // screen position (0-1 normalized)
};
static ActorESPData g_espActors[20];
static int g_espCount = 0;

// Hook: ActorLinker.get_position — capture position khi actor update
// Dùng hook trên get_position để collect data mỗi frame
void (*_orig_GetPos)(void* self);
void hook_GetPos_collect(void* actorLinker) {
    // Không hook get_position vì return type phức tạp
    // Collect trong Update thread thay thế
}

// Hàm collect ESP data (gọi từ loop)
static void CollectESPData() {
    if (!hackESP || !fp_GetMainCamera || !fp_WorldToScreen) return;

    void* camera = fp_GetMainCamera();
    if (!camera) return;

    // Reset
    g_espCount = 0;

    // TODO: iterate actors qua ActorManager
    // Tạm dùng bằng cách hook ActorLinker update
    // (sẽ được populate bởi hook HOK_OnLateUpdate)
}

// ================================================================
// INIT THREAD
// ================================================================
void* Init_Thread(void*) {
    uintptr_t base = 0;
    for (int i = 0; i < 15; i++) {
        base = Tools::GetBaseAddress("libil2cpp.so");
        if (base) break;
        sleep(2);
    }
    if (!base) return nullptr;

    LOGI("[STAR] base=0x%lx", base);

    // === Map Hack ===
    DobbyHook((void*)(base + 0x6BCE384), (void*)hook_SetVisible,
              (void**)&_SetVisible);

    // === Cam Xa ===
    DobbyHook((void*)(base + 0x8D546F4), (void*)hook_GetCameraHeightRateValue,
              (void**)&_GetCameraHeightRateValue);

    // === Mod Skin ===
    // COMDT_HERO_COMMON_INFO.unpack
    DobbyHook((void*)(base + 0x3A10664), (void*)hook_unpack,
              (void**)&_unpack);
    // CRoleInfo.IsCanUseSkin(uint, uint)
    DobbyHook((void*)(base + 0x7E08630), (void*)hook_IsCanUseSkin,
              (void**)&_IsCanUseSkin);
    // CRoleInfo.IsHaveHeroSkin(uint, uint, bool)
    DobbyHook((void*)(base + 0x7E079AC), (void*)hook_IsHaveHeroSkin,
              (void**)&_IsHaveHeroSkin);
    // CRoleInfo.GetWearSkinId()
    DobbyHook((void*)(base + 0x7637D40), (void*)hook_GetWearSkinId,
              (void**)&_GetWearSkinId);

    // === ESP function pointers (không hook, gọi trực tiếp) ===
    fp_GetObjCamp    = (COM_PLAYERCAMP(*)(void*))          (base + 0x8A49630);
    fp_GetPosition   = (Vector3(*)(void*))                 (base + 0x8A490AC);
    fp_WorldToScreen = (Vector2(*)(void*, Vector3))        (base + 0x71C99BC);
    fp_GetMainCamera = (void*(*)())                        (base + 0x94AE0DC);

    LOGI("[STAR] all hooks done");

    while (true) {
        sleep(1);
        if (hackSkin && !hackSkin) {
            // Restore skins khi tắt
            for (auto& e : g_skinSave) {
                if (e.instance)
                    CSProtocol::COMDT_HERO_COMMON_INFO::setWSkinID(e.instance, e.origSkinId);
            }
            g_skinSave.clear();
        }
    }
    return nullptr;
}

// ================================================================
// LIBRARY ENTRY
// ================================================================
__attribute__((constructor))
void lib_main() {
    pthread_t t1, t2;
    pthread_create(&t1, nullptr, socket_server_thread, nullptr);
    pthread_create(&t2, nullptr, Init_Thread, nullptr);
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    return JNI_VERSION_1_6;
}
