#include <iostream>
#include <winsock2.h>
#include <ws2tcpip.h>
#include <vector>
#include <string>
#include <thread>
#include <atomic>
#include <mutex>
#include <mfapi.h>
#include <mftransform.h>
#include <mferror.h>
#include <mfobjects.h>
#include <wmcodecdsp.h>
#include <windows.h>

#pragma comment(lib, "ws2_32.lib")
#pragma comment(lib, "mfplat.lib")
#pragma comment(lib, "mfuuid.lib")
#pragma comment(lib, "wmcodecdspuuid.lib")
#pragma comment(lib, "ole32.lib")
#pragma comment(lib, "user32.lib")
#pragma comment(lib, "gdi32.lib")

#ifndef MF_E_NOT_ACCEPTING
#define MF_E_NOT_ACCEPTING  _HRESULT_TYPEDEF_(0xC00D36B5L)
#endif

std::atomic<bool> g_running(true);
HWND g_hwnd = NULL;
std::atomic<int> g_packetsReceived(0);
std::atomic<int> g_framesDecoded(0);
std::vector<BYTE> g_backBuffer;
std::mutex g_bufferMutex;

class H264Decoder {
    IMFTransform* pDecoder = nullptr;
    DWORD dwIn = 0, dwOut = 0;
    bool isInit = false;
    MFT_OUTPUT_STREAM_INFO outInfo = { 0 };

public:
    H264Decoder() {
        CoInitializeEx(NULL, COINIT_MULTITHREADED);
        MFStartup(MF_VERSION);
        CoCreateInstance(CLSID_CMSH264DecoderMFT, NULL, CLSCTX_INPROC_SERVER, IID_PPV_ARGS(&pDecoder));
        if (pDecoder) pDecoder->GetStreamIDs(1, &dwIn, 1, &dwOut);
    }

    bool init() {
        IMFMediaType *pIn = nullptr, *pOut = nullptr;
        MFCreateMediaType(&pIn);
        pIn->SetGUID(MF_MT_MAJOR_TYPE, MFMediaType_Video);
        pIn->SetGUID(MF_MT_SUBTYPE, MFVideoFormat_H264);
        MFSetAttributeSize(pIn, MF_MT_FRAME_SIZE, 640, 480);
        pDecoder->SetInputType(dwIn, pIn, 0);
        pIn->Release();
        MFCreateMediaType(&pOut);
        pOut->SetGUID(MF_MT_MAJOR_TYPE, MFMediaType_Video);
        pOut->SetGUID(MF_MT_SUBTYPE, MFVideoFormat_YUY2);
        MFSetAttributeSize(pOut, MF_MT_FRAME_SIZE, 640, 480);
        HRESULT hr = pDecoder->SetOutputType(dwOut, pOut, 0);
        pOut->Release();
        if (FAILED(hr)) return false;
        pDecoder->GetOutputStreamInfo(dwOut, &outInfo);
        pDecoder->ProcessMessage(MFT_MESSAGE_NOTIFY_BEGIN_STREAMING, 0);
        isInit = true;
        return true;
    }

    void drain() {
        while (true) {
            MFT_OUTPUT_DATA_BUFFER out = { 0 };
            out.dwStreamID = dwOut;
            IMFSample* pSample = nullptr;
            IMFMediaBuffer* pAlloc = nullptr;
            MFCreateSample(&pSample);
            MFCreateMemoryBuffer(outInfo.cbSize ? outInfo.cbSize : (640*480*2), &pAlloc);
            pSample->AddBuffer(pAlloc);
            out.pSample = pSample;
            DWORD status;
            HRESULT hr = pDecoder->ProcessOutput(0, 1, &out, &status);
            if (hr == S_OK) {
                g_framesDecoded++;
                IMFMediaBuffer* pRes = nullptr; out.pSample->GetBufferByIndex(0, &pRes);
                BYTE* pRaw = nullptr; DWORD len = 0; pRes->Lock(&pRaw, NULL, &len);
                {
                    std::lock_guard<std::mutex> lock(g_bufferMutex);
                    if (g_backBuffer.size() != len) g_backBuffer.resize(len);
                    memcpy(g_backBuffer.data(), pRaw, len);
                }
                pRes->Unlock(); pRes->Release(); out.pSample->Release(); pAlloc->Release();
                if (g_hwnd) InvalidateRect(g_hwnd, NULL, FALSE);
            } else {
                if (hr == MF_E_TRANSFORM_STREAM_CHANGE) {
                    IMFMediaType* pNew = nullptr; pDecoder->GetOutputAvailableType(dwOut, 0, &pNew);
                    pDecoder->SetOutputType(dwOut, pNew, 0);
                    pDecoder->GetOutputStreamInfo(dwOut, &outInfo);
                    pNew->Release();
                }
                out.pSample->Release(); pAlloc->Release();
                if (out.pEvents) out.pEvents->Release();
                break;
            }
        }
    }

    void process(const std::vector<BYTE>& data) {
        if (!pDecoder) return;
        if (!isInit && !init()) return;
        IMFSample* pInSample = nullptr; IMFMediaBuffer* pInBuf = nullptr; BYTE* pD = nullptr;
        MFCreateMemoryBuffer((DWORD)data.size(), &pInBuf);
        pInBuf->Lock(&pD, NULL, NULL); memcpy(pD, data.data(), data.size()); pInBuf->Unlock();
        pInBuf->SetCurrentLength((DWORD)data.size());
        MFCreateSample(&pInSample); pInSample->AddBuffer(pInBuf);
        HRESULT hr = pDecoder->ProcessInput(dwIn, pInSample, 0);
        drain();
        if (hr == MF_E_NOT_ACCEPTING) { drain(); pDecoder->ProcessInput(dwIn, pInSample, 0); }
        pInSample->Release(); pInBuf->Release();
    }

    ~H264Decoder() { if (pDecoder) pDecoder->Release(); MFShutdown(); CoUninitialize(); }
};

LRESULT CALLBACK WindowProc(HWND hwnd, UINT uMsg, WPARAM wParam, LPARAM lParam) {
    if (uMsg == WM_DESTROY) { g_running = false; PostQuitMessage(0); return 0; }
    if (uMsg == WM_PAINT) {
        PAINTSTRUCT ps; HDC hdc = BeginPaint(hwnd, &ps);
        {
            std::lock_guard<std::mutex> lock(g_bufferMutex);
            if (!g_backBuffer.empty()) {
                BITMAPINFO bmi = {0}; bmi.bmiHeader.biSize = sizeof(BITMAPINFOHEADER);
                bmi.bmiHeader.biWidth = 640; bmi.bmiHeader.biHeight = -480; bmi.bmiHeader.biPlanes = 1;
                bmi.bmiHeader.biBitCount = 16; bmi.bmiHeader.biCompression = MAKEFOURCC('Y','U','Y','2');
                StretchDIBits(hdc, 0, 0, 1280, 720, 0, 0, 640, 480, g_backBuffer.data(), &bmi, DIB_RGB_COLORS, SRCCOPY);
            } else {
                FillRect(hdc, &ps.rcPaint, (HBRUSH)GetStockObject(BLACK_BRUSH));
                SetTextColor(hdc, RGB(0, 255, 0)); SetBkMode(hdc, TRANSPARENT);
                std::string m = "SYNCING... Packets: " + std::to_string(g_packetsReceived) + " | Frames: " + std::to_string(g_framesDecoded);
                DrawTextA(hdc, m.c_str(), -1, &ps.rcPaint, DT_CENTER|DT_VCENTER|DT_SINGLELINE);
            }
        }
        EndPaint(hwnd, &ps); return 0;
    }
    return DefWindowProcW(hwnd, uMsg, wParam, lParam);
}

void videoThread(int port) {
    H264Decoder dec; SOCKET s = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    int b = 1024*1024*8; setsockopt(s, SOL_SOCKET, SO_RCVBUF, (char*)&b, 4);
    sockaddr_in a = {AF_INET, htons(port)}; a.sin_addr.s_addr = INADDR_ANY; bind(s, (sockaddr*)&a, sizeof(a));
    char buf[2048]; std::vector<BYTE> frame;
    while (g_running) {
        int r = recvfrom(s, buf, sizeof(buf), 0, NULL, NULL);
        if (r > 13) {
            g_packetsReceived++; frame.insert(frame.end(), buf + 13, buf + r);
            if (buf[12] & 0x02) { dec.process(frame); frame.clear(); }
            if (g_packetsReceived % 60 == 0 && g_hwnd) InvalidateRect(g_hwnd, NULL, FALSE);
        }
    }
    closesocket(s);
}

int main(int argc, char** argv) {
    WSADATA w; WSAStartup(0x0202, &w); const char* ip = (argc > 1) ? argv[1] : "127.0.0.1";
    WNDCLASSW wc = {0}; wc.lpfnWndProc = WindowProc; wc.hInstance = GetModuleHandle(NULL); wc.lpszClassName = L"WebcamWin";
    RegisterClassW(&wc);
    g_hwnd = CreateWindowExW(0, L"WebcamWin", L"Phone Webcam", WS_OVERLAPPEDWINDOW|WS_VISIBLE, 100, 100, 1300, 760, NULL, NULL, GetModuleHandle(NULL), NULL);
    std::thread v(videoThread, 6000);
    SOCKET ctrl = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    sockaddr_in caddr = {AF_INET, htons(8080)}; inet_pton(AF_INET, ip, &caddr.sin_addr.s_addr);
    connect(ctrl, (sockaddr*)&caddr, sizeof(caddr));
    MSG msg;
    while (g_running && GetMessageW(&msg, NULL, 0, 0)) {
        if (GetAsyncKeyState('S') & 0x8000) {
            std::string s = "{\"type\":\"switch_camera\"}"; uint32_t l = htonl((uint32_t)s.length());
            send(ctrl, (char*)&l, 4, 0); send(ctrl, s.c_str(), (int)s.length(), 0); Sleep(500);
        }
        TranslateMessage(&msg); DispatchMessageW(&msg);
    }
    g_running = false; closesocket(ctrl); v.join(); WSACleanup(); return 0;
}
