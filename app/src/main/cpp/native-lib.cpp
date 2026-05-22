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

// ========== HOK OFFSETS ==========
// ActorLinker._logicVisible  @ 0x559
// ActorLinker._meshVisible   @ 0x55A
#define OFF_LOGIC_VIS 0x559
#define OFF_MESH_VIS  0x55A

// ========== FUNCTION POINTERS ==========
static int  (*fp_get_objCamp)(void*) = nullptr; // 0x6216C2C
static int  (*fp_get_objType)(void*) = nullptr; // 0x6216C34

// ================================================================
// HOOK 1: get_Visible() — RVA 0x621827C
// Game gọi cái này để hỏi "actor có đang hiển thị không?"
// → Ta trả về true cho hero địch luôn
// ================================================================
bool (*old_get_Visible)(void*);
bool hook_get_Visible(void *instance) {
    if (instance && hackMap && fp_get_objCamp && fp_get_objType) {
        if (fp_get_objType(instance) == 0) {        // chỉ hero
            int camp = fp_get_objCamp(instance);
            if (camp == 1 || camp == 2) return true; // force visible
        }
    }
    return old_get_Visible(instance);
}

// ================================================================
// HOOK 2: get_MeshVisible() — RVA 0x6218284
// Kiểm soát render model ra màn hình
// ================================================================
bool (*old_get_MeshVisible)(void*);
bool hook_get_MeshVisible(void *instance) {
    if (instance && hackMap && fp_get_objCamp && fp_get_objType) {
        if (fp_get_objType(instance) == 0) {
            int camp = fp_get_objCamp(instance);
            if (camp == 1 || camp == 2) return true;
        }
    }
    return old_get_MeshVisible(instance);
}

// ================================================================
// HOOK 3: HOK_OnLateUpdate() — RVA 0x62164F8
// Chạy mỗi frame: force offset trực tiếp phòng game ghi tắt
// ================================================================
void (*old_HOK_OnLateUpdate)(void*, int);
void hook_HOK_OnLateUpdate(void *instance, int nDelta) {
    old_HOK_OnLateUpdate(instance, nDelta);
    if (!instance || !hackMap || !fp_get_objCamp || !fp_get_objType) return;
    if (fp_get_objType(instance) != 0) return;

    int camp = fp_get_objCamp(instance);
    if (camp == 1 || camp == 2) {
        *(bool*)((uintptr_t)instance + OFF_LOGIC_VIS) = true;
        *(bool*)((uintptr_t)instance + OFF_MESH_VIS)  = true;
    }
}

// ================================================================
// HOOK 4: SetVisible() — RVA 0x62182B4
// Chặn luôn khi game cố gọi SetVisible(false, false)
// ================================================================
void (*old_SetVisible)(void*, bool, bool);
void hook_SetVisible(void *instance, bool bLogic, bool bMesh) {
    if (instance && hackMap && fp_get_objCamp && fp_get_objType) {
        if (fp_get_objType(instance) == 0) {
            int camp = fp_get_objCamp(instance);
            if (camp == 1 || camp == 2) { bLogic = true; bMesh = true; }
        }
    }
    old_SetVisible(instance, bLogic, bMesh);
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

    // Getter hooks — game hỏi visibility → ta trả lời true
    DobbyHook((void*)(base + 0x621827C), (void*)hook_get_Visible,     (void**)&old_get_Visible);
    DobbyHook((void*)(base + 0x6218284), (void*)hook_get_MeshVisible,  (void**)&old_get_MeshVisible);

    // Per-frame force via offset
    DobbyHook((void*)(base + 0x62164F8), (void*)hook_HOK_OnLateUpdate, (void**)&old_HOK_OnLateUpdate);

    // Chặn SetVisible khi game cố ẩn
    DobbyHook((void*)(base + 0x62182B4), (void*)hook_SetVisible,       (void**)&old_SetVisible);

    // Function pointers
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
