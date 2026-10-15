#include <jni.h>
#include <pthread.h>
#include <android/log.h>
#include <GLES2/gl2.h>
#include <cmath>
#include <unistd.h>
#include <dlfcn.h>
#include <vector>

#define TAG "SelteckCheat"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

struct Vector2 {
    float x, y;
};

struct Vector3 {
    float x, y, z;
    Vector3() : x(0), y(0), z(0) {}
    Vector3(float x, float y, float z) : x(x), y(y), z(z) {}
};

struct Matrix4x4 {
    float m[16];
};

struct CheatConfig {
    bool espBox = true;
    bool espHealth = true;
    bool espTracer = true;
    bool triggerBotEnabled = true;
    float triggerFov = 15.0f;
};

CheatConfig config;
uintptr_t il2cppBase = 0;

struct PlayerData {
    uintptr_t entityInstance;
    Vector3 headPosition;
    Vector3 footPosition;
    int health;
    bool isTeam;
};

std::vector<PlayerData> g_Players;

uintptr_t GetModuleBase(const char* moduleName) {
    void* handle = dlopen(moduleName, RTLD_NOLOCAL | RTLD_LAZY);
    if (!handle) return 0;

    uintptr_t baseAddress = 0;
    char filename[32];
    snprintf(filename, sizeof(filename), "/proc/%d/maps", getpid());
    FILE* fp = fopen(filename, "r");
    if (fp) {
        char line[512];
        while (fgets(line, sizeof(line), fp)) {
            if (strstr(line, moduleName)) {
                baseAddress = strtoul(line, NULL, 16);
                break;
            }
        }
        fclose(fp);
    }
    dlclose(handle);
    return baseAddress;
}

bool WorldToScreen(Vector3 worldPos, Vector2 &screenPos, Matrix4x4 matrix, int screenWidth, int screenHeight) {
    float w = matrix.m[3] * worldPos.x + matrix.m[7] * worldPos.y + matrix.m[11] * worldPos.z + matrix.m[15];
    if (w < 0.01f) return false;

    float x = matrix.m[0] * worldPos.x + matrix.m[4] * worldPos.y + matrix.m[8] * worldPos.z + matrix.m[12];
    float y = matrix.m[1] * worldPos.x + matrix.m[5] * worldPos.y + matrix.m[9] * worldPos.z + matrix.m[13];

    float invW = 1.0f / w;
    screenPos.x = (screenWidth / 2.0f) + (x * invW * (screenWidth / 2.0f));
    screenPos.y = (screenHeight / 2.0f) - (y * invW * (screenHeight / 2.0f));

    return true;
}

void RunTriggerBot(const std::vector<PlayerData>& players, int screenWidth, int screenHeight, Matrix4x4 viewMatrix) {
    if (!config.triggerBotEnabled) return;

    Vector2 screenCenter = { (float)screenWidth / 2.0f, (float)screenHeight / 2.0f };

    for (const auto& player : players) {
        if (player.health <= 0 || player.isTeam) continue;

        Vector2 screenHead;
        if (WorldToScreen(player.headPosition, screenHead, viewMatrix, screenWidth, screenHeight)) {
            float distanceToCrosshair = sqrt(pow(screenHead.x - screenCenter.x, 2) + pow(screenHead.y - screenCenter.y, 2));

            if (distanceToCrosshair <= config.triggerFov) {
                // Срабатывание триггера по цели в прицеле
                break;
            }
        }
    }
}

typedef EGLBoolean (*eglSwapBuffers_t)(EGLDisplay dpy, EGLSurface surface);
eglSwapBuffers_t orig_eglSwapBuffers = nullptr;

EGLBoolean hooked_eglSwapBuffers(EGLDisplay dpy, EGLSurface surface) {
    GLint viewport[4];
    glGetIntegerv(GL_VIEWPORT, viewport);
    int screenWidth = viewport[2];
    int screenHeight = viewport[3];

    Matrix4x4 viewMatrix;

    if (config.espBox || config.espHealth || config.espTracer) {
        for (const auto& player : g_Players) {
            if (player.health <= 0) continue;

            Vector2 screenHead, screenFoot;
            if (WorldToScreen(player.headPosition, screenHead, viewMatrix, screenWidth, screenHeight) &&
                WorldToScreen(player.footPosition, screenFoot, viewMatrix, screenWidth, screenHeight)) {

                float boxHeight = abs(screenFoot.y - screenHead.y);
                float boxWidth = boxHeight / 2.0f;
                // Рендер Box ESP
            }
        }
    }

    RunTriggerBot(g_Players, screenWidth, screenHeight, viewMatrix);

    return orig_eglSwapBuffers(dpy, surface);
}

void* MainThread(void* arg) {
    while (il2cppBase == 0) {
        il2cppBase = GetModuleBase("libil2cpp.so");
        usleep(500000);
    }
    LOGI("libil2cpp.so найден: 0%lX", il2cppBase);

    while (true) {
        usleep(15000);
    }
    return nullptr;
}

void* HookThread(void* arg) {
    void* eglHandle = nullptr;
    while (!eglHandle) {
        eglHandle = dlopen("libEGL.so", RTLD_NOLOCAL | RTLD_LAZY);
        if (!eglHandle) usleep(500000);
    }

    auto p_eglSwapBuffers = (eglSwapBuffers_t)dlsym(eglHandle, "eglSwapBuffers");
    if (p_eglSwapBuffers) {
        orig_eglSwapBuffers = p_eglSwapBuffers;
        LOGI("Графический хук eglSwapBuffers установлен!");
    }
    return nullptr;
}

__attribute__((constructor)) void Init() {
    pthread_t mainThread, hookThread;
    pthread_create(&mainThread, nullptr, MainThread, nullptr);
    pthread_create(&hookThread, nullptr, HookThread, nullptr);
}
