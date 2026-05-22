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
bool hackMap = false;

// ========== HOK ENUMS ==========
// COM_PLAYERCAMP: 0=Mid/neutral, 1=Camp1(blue), 2=Camp2(red)
enum HOK_PLAYERCAMP { CAMP_MID = 0, CAMP_1 = 1, CAMP_2 = 2 };

// ========== FUNCTION POINTERS ==========
// ActorLinker.get_objCamp()  RVA: 0x6216C2C
static int (*fp_get_objCamp)(void *instance) = nullptr;

// ========== MAP HACK ==========
// ActorLinker.SetVisible(bool bLogicVisible, bool bMeshVisible)
// RVA: 0x62182B4
// Cách này giống hệt AOV: hook SetVisible, force visible = true cho địch
void (*old_SetVisible)(void *instance, bool bLogicVisible, bool bMeshVisible);
void SetVisible(void *instance, bool bLogicVisible, bool bMeshVisible) {
    if (instance != nullptr && hackMap && fp_get_objCamp != nullptr) {
        int camp = fp_get_objCamp(instance);
        // Force visible cho cả 2 camp (địch lẫn đồng đội — toàn map)
        if (camp == CAMP_1 || camp == CAMP_2) {
            bLogicVisible = true;
            bMeshVisible  = true;
        }
    }
    old_SetVisible(instance, bLogicVisible, bMeshVisible);
}

// ========== THREAD CHÍNH ==========
void *Init_Thread(void *) {
    uintptr_t base = 0;
    for (int i = 0; i < 15; i++) {
        base = Tools::GetBaseAddress("libil2cpp.so");
        if (base != 0) break;
        sleep(2);
    }
    if (base == 0) return nullptr;

    // Hook Map Hack — ActorLinker.SetVisible(bool, bool)
    DobbyHook(
        (void *)(base + 0x62182B4),
        (void *) SetVisible,
        (void **) &old_SetVisible
    );

    // Function pointer: get_objCamp() để biết camp trong hook
    fp_get_objCamp = (int(*)(void*)) (base + 0x6216C2C);

    while (true) { sleep(1); }
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

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    return JNI_VERSION_1_6;
}