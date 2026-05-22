#include <EGL/egl.h>
#include <vector>
#include <cstring>
#include <cstdio>
#include <pthread.h>
#include <unistd.h>
#include <jni.h>
#include "Includes/obfuscate.h"
#include "Includes/Logger.h"
#include "Includes/Utils.h"
#include "STARCOOLX/Call_Me.h"
#include "STARCOOL.h"
#include "SocketControl.h"

// ================================================================
// FEATURE FLAGS
// ================================================================
bool hackMap   = false;
bool hackCamXa = false;
float camXaValue = 0.0f;
bool hackSkin  = false;  // Lobby-only skin unlock
bool hackESP   = false;

// ================================================================
// STRUCTS
// ================================================================
struct VInt3 { int X, Y, Z; };

// ── VInt2Vector: convert VInt3 → Unity world position ─────────
static int dem(int num) {
    int div = 1, n = num < 0 ? -num : num;
    if (n == 0) return 10;
    while (n != 0) { n /= 10; div *= 10; }
    return div;
}
static Vector3 VInt2Vector(VInt3 loc, VInt3 fwd) {
    float x = (float)(loc.X * dem(fwd.X) + fwd.X) / (1000.0f * dem(fwd.X));
    float y = (float)(loc.Y * dem(fwd.Y) + fwd.Y) / (1000.0f * dem(fwd.Y));
    float z = (float)(loc.Z * dem(fwd.Z) + fwd.Z) / (1000.0f * dem(fwd.Z));
    return Vector3(x, y, z);
}

// ================================================================
// ESP MANAGER  (LActorRoot-based, always-updated positions)
// ================================================================
struct EspActor { void *lActorRoot; };

static std::vector<EspActor> g_espEnemies;
static void*                 g_myPlayer = nullptr;
static pthread_mutex_t       g_espLock  = PTHREAD_MUTEX_INITIALIZER;

// ── Function pointers ─────────────────────────────────────────
static int      (*fp_get_objCamp)(void*)  = nullptr; // ActorLinker/LActorRoot
static VInt3    (*fp_get_location)(void*) = nullptr; // LActorRoot.get_location
static VInt3    (*fp_get_forward)(void*)  = nullptr; // LActorRoot.get_forward
static bool     (*fp_get_bActive)(void*)  = nullptr; // LActorRoot.get_bActive
static Vector3  (*fp_WorldToScreen)(void*, Vector3) = nullptr; // static W2S
static void*    (*fp_Camera_main)()       = nullptr; // Camera.get_main
static int      (*fp_get_actorHp)(void*)     = nullptr;
static int      (*fp_get_actorHpTotal)(void*)= nullptr;
static bool     (*fp_IsHostPlayer)(void*) = nullptr; // ActorLinker.IsHostPlayer
static bool     (*fp_get_bVisible)(void*) = nullptr; // ActorLinker.get_bVisible

// ================================================================
// ESP HOOKS
// ================================================================

// Hook LActorRoot.UpdateLogic(int delta) RVA 0x8A47648
// Called every frame for every active LActorRoot (hero, minion, tower…)
static void (*old_LActorRoot_UpdateLogic)(void*, int);
static void hook_LActorRoot_UpdateLogic(void *instance, int delta) {
    old_LActorRoot_UpdateLogic(instance, delta);
    if (!instance || !hackESP) return;

    // Camp filter: 1=ally, 2=enemy (heroes); 0=neutral/NPC
    int camp = fp_get_objCamp ? fp_get_objCamp(instance) : 0;
    if (camp != 1 && camp != 2) return;

    pthread_mutex_lock(&g_espLock);
    // Check if already tracked
    for (auto& e : g_espEnemies) {
        if (e.lActorRoot == instance) {
            pthread_mutex_unlock(&g_espLock);
            return;
        }
    }
    if (g_espEnemies.size() < 20)
        g_espEnemies.push_back({instance});
    pthread_mutex_unlock(&g_espLock);
}

// Hook SetVisible (map hack) RVA 0x6BCE384
static void (*old_SetVisible)(void*, int, bool, bool);
static void hook_SetVisible(void *instance, int camp, bool bVisible, bool forceSync) {
    if (instance && hackMap) {
        if (camp == 1 || camp == 2) bVisible = true;
    }
    old_SetVisible(instance, camp, bVisible, forceSync);
}

// Hook GetCameraHeightRateValue RVA 0x8D546F4
static float (*old_GetCamHeight)(void*, int);
static float hook_GetCamHeight(void *instance, int type) {
    float orig = old_GetCamHeight(instance, type);
    if (instance && hackCamXa) return orig + camXaValue;
    return orig;
}

// ================================================================
// SKIN — lobby only, no config needed
// Hooks: IsCanUseSkin + IsHaveHeroSkin → always true
// User selects hero + skin themselves in lobby
// ================================================================
static bool (*old_IsCanUseSkin)(void*, uint32_t, uint32_t);
static bool hook_IsCanUseSkin(void *self, uint32_t heroId, uint32_t skinId) {
    if (hackSkin) return true;
    return old_IsCanUseSkin(self, heroId, skinId);
}

static bool (*old_IsHaveHeroSkin)(void*, uint32_t, uint32_t, bool);
static bool hook_IsHaveHeroSkin(void *self, uint32_t heroId, uint32_t skinId, bool incTime) {
    if (hackSkin) return true;
    return old_IsHaveHeroSkin(self, heroId, skinId, incTime);
}

// ================================================================
// JNI: GetEspActors — Android side calls this to get screen positions
// Returns float[] = [n, camp1,sx1,sy1,hp1,maxhp1, camp2,sx2,sy2,hp2,maxhp2, ...]
// ================================================================
static jfloatArray GetEspActors(JNIEnv *env, jclass, jint screenW, jint screenH) {
    pthread_mutex_lock(&g_espLock);

    void *cam = fp_Camera_main ? fp_Camera_main() : nullptr;
    int count = 0;
    float data[1 + 20 * 5]; // max 20 actors, 5 floats each
    data[0] = 0;

    if (cam && fp_WorldToScreen && fp_get_location && fp_get_forward) {
        for (auto &e : g_espEnemies) {
            void *actor = e.lActorRoot;
            if (!actor) continue;

            int camp = fp_get_objCamp ? fp_get_objCamp(actor) : 0;
            if (camp != 2) continue; // only enemies (camp 2)

            VInt3 loc = fp_get_location(actor);
            VInt3 fwd = fp_get_forward(actor);
            Vector3 worldPos = VInt2Vector(loc, fwd);
            Vector3 screenPos = fp_WorldToScreen(cam, worldPos);

            if (screenPos.Z <= 0) continue; // behind camera

            float sx = screenPos.X;
            float sy = (float)screenH - screenPos.Y;
            if (sx < -200 || sx > screenW + 200 || sy < -200 || sy > screenH + 200) continue;

            // HP from ValueComponent (field offset 0x308 in LActorRoot)
            void *vc = *(void**)((uintptr_t)actor + 0x308);
            int hp = 0, mhp = 100;
            if (vc && fp_get_actorHp && fp_get_actorHpTotal) {
                hp  = fp_get_actorHp(vc);
                mhp = fp_get_actorHpTotal(vc);
                if (mhp <= 0) mhp = 100;
            }

            int idx = 1 + count * 5;
            data[idx + 0] = (float)camp;
            data[idx + 1] = sx;
            data[idx + 2] = sy;
            data[idx + 3] = (float)hp;
            data[idx + 4] = (float)mhp;
            count++;
            if (count >= 20) break;
        }
    }
    data[0] = (float)count;
    pthread_mutex_unlock(&g_espLock);

    jfloatArray arr = env->NewFloatArray(1 + count * 5);
    if (arr) env->SetFloatArrayRegion(arr, 0, 1 + count * 5, data);
    return arr;
}

// ================================================================
// INIT THREAD
// ================================================================
static void *Init_Thread(void *) {
    uintptr_t base = 0;
    for (int i = 0; i < 15; i++) {
        base = Tools::GetBaseAddress("libil2cpp.so");
        if (base) break;
        sleep(2);
    }
    if (!base) return nullptr;

    LOGI("[STAR] base=0x%lx", base);

    // Map Hack
    DobbyHook((void*)(base+0x6BCE384), (void*)hook_SetVisible,   (void**)&old_SetVisible);
    // Cam Xa
    DobbyHook((void*)(base+0x8D546F4), (void*)hook_GetCamHeight, (void**)&old_GetCamHeight);
    // Skin unlock (lobby only)
    DobbyHook((void*)(base+0x7E08630), (void*)hook_IsCanUseSkin,   (void**)&old_IsCanUseSkin);
    DobbyHook((void*)(base+0x7E079AC), (void*)hook_IsHaveHeroSkin, (void**)&old_IsHaveHeroSkin);
    // ESP actor tracking
    DobbyHook((void*)(base+0x8A47648), (void*)hook_LActorRoot_UpdateLogic, (void**)&old_LActorRoot_UpdateLogic);

    // ESP function pointers (direct call, not hooked)
    fp_get_objCamp    = (int(*)(void*))    (base + 0x8A49630);
    fp_get_location   = (VInt3(*)(void*))  (base + 0x8A48CE0);
    fp_get_forward    = (VInt3(*)(void*))  (base + 0x8A48B58);
    fp_get_bActive    = (bool(*)(void*))   (base + 0x6BAADA8);
    fp_WorldToScreen  = (Vector3(*)(void*, Vector3))(base + 0x71C99BC);
    fp_Camera_main    = (void*(*)())       (base + 0x94AE0DC);
    fp_get_actorHp    = (int(*)(void*))    (base + 0x8A7B378);
    fp_get_actorHpTotal=(int(*)(void*))    (base + 0x8A7B388);
    fp_IsHostPlayer   = (bool(*)(void*))   (base + 0x8A52478);
    fp_get_bVisible   = (bool(*)(void*))   (base + 0x8A49504);

    LOGI("[STAR] all hooks done");
    return nullptr;
}

// ================================================================
// SOCKET THREAD
// ================================================================
// (defined in SocketControl.h)

// ================================================================
// LIBRARY ENTRY
// ================================================================
__attribute__((constructor))
void lib_main() {
    pthread_t t1, t2;
    pthread_create(&t1, nullptr, socket_server_thread, nullptr);
    pthread_create(&t2, nullptr, Init_Thread, nullptr);
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    vm->GetEnv((void**)&env, JNI_VERSION_1_6);

    // Register GetEspActors for Android drawing
    JNINativeMethod methods[] = {
        {(char*)"GetEspActors", (char*)"(II)[F", (void*)GetEspActors}
    };
    jclass clazz = env->FindClass("com/star/android/service/FloatingService");
    if (clazz) env->RegisterNatives(clazz, methods, 1);

    return JNI_VERSION_1_6;
}
