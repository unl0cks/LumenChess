#include <jni.h>

#include <cstdio>
#include <iostream>
#include <mutex>
#include <unistd.h>

extern "C" int lumen_reckless09_run();

namespace {
std::mutex engine_mutex;

class StandardFdRedirect {
   public:
    StandardFdRedirect(int input_fd, int output_fd) {
        std::cout.flush();
        std::cerr.flush();
        std::fflush(nullptr);

        saved_stdin_  = dup(STDIN_FILENO);
        saved_stdout_ = dup(STDOUT_FILENO);
        if (saved_stdin_ < 0 || saved_stdout_ < 0 || dup2(input_fd, STDIN_FILENO) < 0
            || dup2(output_fd, STDOUT_FILENO) < 0)
        {
            valid_ = false;
        }

        close(input_fd);
        close(output_fd);
        std::cin.clear();
        std::cout.clear();
    }

    ~StandardFdRedirect() {
        std::cout.flush();
        std::fflush(nullptr);
        if (saved_stdin_ >= 0)
        {
            dup2(saved_stdin_, STDIN_FILENO);
            close(saved_stdin_);
        }
        if (saved_stdout_ >= 0)
        {
            dup2(saved_stdout_, STDOUT_FILENO);
            close(saved_stdout_);
        }
        std::cin.clear();
        std::cout.clear();
    }

    bool valid() const { return valid_; }

   private:
    int  saved_stdin_  = -1;
    int  saved_stdout_ = -1;
    bool valid_        = true;
};
}  // namespace

extern "C" JNIEXPORT jint JNICALL
Java_dev_lumenchess_engine_host_Reckless09NativeBridge_run(
  JNIEnv*, jobject, jint input_fd, jint output_fd) {
    std::lock_guard<std::mutex> lock(engine_mutex);
    StandardFdRedirect redirect(input_fd, output_fd);
    if (!redirect.valid())
        return 2;

    return static_cast<jint>(lumen_reckless09_run());
}
