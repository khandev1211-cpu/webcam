cmake_minimum_required(VERSION 3.20)
project(PhoneWebcamReceiver LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Windows 11 SDK is required for Virtual Camera APIs
if(WIN32)
    add_definitions(-D_WIN32_WINNT=0x0A00) # Windows 10+
endif()

add_executable(PhoneWebcamReceiver src/main.cpp)

# Link against Media Foundation and Sockets
target_link_libraries(PhoneWebcamReceiver PRIVATE
    mf
    mfplat
    mfuuid
    ws2_32
)
