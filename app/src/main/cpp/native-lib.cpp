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

// ========== FLAGS ==========
bool hackMap = false;

// ========== OFFSETS ==========
#define OFF_LOGIC_VIS  0x559   // ActorLinker._logicVisible (bool)
#define OFF_MESH_VIS   0x55A   // ActorLinker._meshVisible  (bool)

// ========== FUNCTION POINTERS ==========
// ActorLinker.get_objCamp()  RVA: 0x6216C2C
// ActorLinker.get_objType()  RVA: 0x6216C34  (0=hero)
static int (*fp_get_objCamp)(void*) = nullptr;
static int (*fp_get_objType)(void*) = nullptr;

// ================================================================
// HOOK DUY NHẤT: HOK_OnLateUpdate
// RVA: 0x62164F8 — đã confirmed hoạt động từ session trước
// Fire mỗi frame cho mỗi actor → force offset trực tiếp
// KHÔNG hook get_Visible/SetVisible (hàm quá nhỏ → crash Dobby)
// ================================================================
void (*old_HOK_OnLateUpdate)(void*, int);
void hook_HOK_OnLateUpdate(void *instance, int nDelta) {
    old_HOK_OnLateUpdate(instance, nDelta);

    if (!instance || !hackMap) return;
    if (!fp_get_objCamp || !fp_get_objType) return;
    if (fp_get_objType(instance) != 0) return; // 0=hero only

    int camp = fp_get_objCamp(instance);
    if (camp == 1 || camp == 2) {
        *(bool*)((uintptr_t)instance + OFF_LOGIC_VIS) = true;
        *(bool*)((uintptr_t)instance + OFF_MESH_VIS)  = true;
    }
}

// ================================================================
// INIT THREAD
// ================================================================
void *Init_Thread(void *) {
    uintptr_t base = 0;
    for (int i = 0; i < 15; i++) {
        base = Tools::GetBaseAddress("libil2cpp.so");
        if (base != 0) break;
        sleep(2);
    }
    if (base == 0) return nullptr;

    // Chỉ hook HOK_OnLateUpdate — an toàn, đã verified
    DobbyHook(
        (void*)(base + 0x62164F8),
        (void*)hook_HOK_OnLateUpdate,
        (void**)&old_HOK_OnLateUpdate
    );

    // Function pointers (direct call, không hook)
    fp_get_objCamp = (int(*)(void*))(base + 0x6216C2C);
    fp_get_objType = (int(*)(void*))(base + 0x6216C34);

    while (true) { sleep(1); }
    return nullptr;
}

// ================================================================
// LIBRARY ENTRY
// ================================================================
__attribute__((constructor))
void lib_main() {
    pthread_t ptid, myThread;
    pthread_create(&ptid,     NULL, socket_server_thread, NULL);
    pthread_create(&myThread, NULL, Init_Thread,          NULL);
}

extern "C" JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* vm, void* reserved) { return JNI_VERSION_1_6; }
