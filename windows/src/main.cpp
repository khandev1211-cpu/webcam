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

std::atomic<bool> g_running(true);
HWND g_hwnd = NULL;
std::atomic<int> g_packetsReceived(0);
std::atomic<int> g_framesDecoded(0);

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

        // Try YUY2 then RGB32
        GUID formats[] = { MFVideoFormat_YUY2, MFVideoFormat_RGB32 };
        HRESULT hr = E_FAIL;
        for (auto f : formats) {
            MFCreateMediaType(&pOut);
            pOut->SetGUID(MF_MT_MAJOR_TYPE, MFMediaType_Video);
            pOut->SetGUID(MF_MT_SUBTYPE, f);
            MFSetAttributeSize(pOut, MF_MT_FRAME_SIZE, 640, 480);
            hr = pDecoder->SetOutputType(dwOut, pOut, 0);
            pOut->Release();
            if (SUCCEEDED(hr)) break;
        }

        if (FAILED(hr)) return false;
        pDecoder->GetOutputStreamInfo(dwOut, &outInfo);
        pDecoder->ProcessMessage(MFT_MESSAGE_NOTIFY_BEGIN_STREAMING, 0);
        isInit = true;
        return true;
    }

    void render(IMFSample* pSample) {
        IMFMediaBuffer* pBuf = nullptr;
        pSample->GetBufferByIndex(0, &pBuf);
        BYTE* pRaw = nullptr; DWORD rawLen = 0;
        pBuf->Lock(&pRaw, NULL, &rawLen);

        HDC hdc = GetDC(g_hwnd);
        BITMAPINFO bmi = { 0 };
        bmi.bmiHeader.biSize = sizeof(BITMAPINFOHEADER);
        bmi.bmiHeader.biWidth = 640;
        bmi.bmiHeader.biHeight = -480;
        bmi.bmiHeader.biPlanes = 1;
        bmi.bmiHeader.biBitCount = 16; // YUY2
        bmi.bmiHeader.biCompression = MAKEFOURCC('Y','U','Y','2');
        StretchDIBits(hdc, 0, 0, 1280, 720, 0, 0, 640, 480, pRaw, &bmi, DIB_RGB_COLORS, SRCCOPY);
        ReleaseDC(g_hwnd, hdc);

        pBuf->Unlock(); pBuf->Release();
    }

    void process(const std::vector<BYTE>& data) {
        if (!pDecoder) return;
        if (!isInit && !init()) return;

        IMFSample* pSample = nullptr;
        IMFMediaBuffer* pBuf = nullptr;
        BYTE* pDest = nullptr;
        MFCreateMemoryBuffer((DWORD)data.size(), &pBuf);
        pBuf->Lock(&pDest, NULL, NULL);
        memcpy(pDest, data.data(), data.size());
        pBuf->Unlock();
        pBuf->SetCurrentLength((DWORD)data.size());
        MFCreateSample(&pSample);
        pSample->AddBuffer(pBuf);

        if (SUCCEEDED(pDecoder->ProcessInput(dwIn, pSample, 0))) {
            while (true) {
                MFT_OUTPUT_DATA_BUFFER out = { 0 };
                out.dwStreamID = dwOut;
                IMFSample* pOutSample = nullptr;
                IMFMediaBuffer* pOutBufAlloc = nullptr;
                MFCreateSample(&pOutSample);
                MFCreateMemoryBuffer(outInfo.cbSize, &pOutBufAlloc);
                pOutSample->AddBuffer(pOutBufAlloc);
                out.pSample = pOutSample;

                DWORD status;
                if (pDecoder->ProcessOutput(0, 1, &out, &status) == S_OK) {
                    g_framesDecoded++;
                    render(out.pSample);
                    out.pSample->Release();
                } else {
                    out.pSample->Release();
                    pOutBufAlloc->Release();
                    if (out.pEvents) out.pEvents->Release();
                    break;
                }
                pOutBufAlloc->Release();
            }
        }
        pSample->Release(); pBuf->Release();
    }

    ~H264Decoder() { if (pDecoder) pDecoder->Release(); MFShutdown(); CoUninitialize(); }
};

LRESULT CALLBACK WindowProc(HWND hwnd, UINT uMsg, WPARAM wParam, LPARAM lParam) {
    if (uMsg == WM_DESTROY) { g_running = false; PostQuitMessage(0); return 0; }
    if (uMsg == WM_PAINT) {
        PAINTSTRUCT ps;
        HDC hdc = BeginPaint(hwnd, &ps);
        FillRect(hdc, &ps.rcPaint, (HBRUSH)GetStockObject(BLACK_BRUSH));
        SetTextColor(hdc, RGB(0, 255, 0));
        SetBkMode(hdc, TRANSPARENT);
        std::string msg = "STREAMING... Packets: " + std::to_string(g_packetsReceived) + " | Frames: " + std::to_string(g_framesDecoded);
        DrawTextA(hdc, msg.c_str(), -1, &ps.rcPaint, DT_CENTER | DT_VCENTER | DT_SINGLELINE);
        EndPaint(hwnd, &ps);
        return 0;
    }
    return DefWindowProcW(hwnd, uMsg, wParam, lParam);
}

void videoThread(int port) {
    H264Decoder dec;
    SOCKET s = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    int b = 1024 * 1024 * 8; setsockopt(s, SOL_SOCKET, SO_RCVBUF, (char*)&b, 4);
    sockaddr_in a = { AF_INET, htons(port) }; a.sin_addr.s_addr = INADDR_ANY;
    bind(s, (sockaddr*)&a, sizeof(a));
    char buf[2048]; std::vector<BYTE> frame;
    while (g_running) {
        int r = recvfrom(s, buf, sizeof(buf), 0, NULL, NULL);
        if (r > 13) {
            g_packetsReceived++;
            frame.insert(frame.end(), buf + 13, buf + r);
            if (buf[12] & 0x02) { dec.process(frame); frame.clear(); }
            if (g_packetsReceived % 30 == 0 && g_hwnd) InvalidateRect(g_hwnd, NULL, FALSE);
        }
    }
    closesocket(s);
}

int main(int argc, char** argv) {
    WSADATA w; WSAStartup(0x0202, &w);
    const char* ip = (argc > 1) ? argv[1] : "127.0.0.1";
    WNDCLASSW wc = { 0 }; wc.lpfnWndProc = WindowProc; wc.hInstance = GetModuleHandle(NULL); wc.lpszClassName = L"WebcamWin";
    wc.hCursor = LoadCursor(NULL, IDC_ARROW);
    RegisterClassW(&wc);
    g_hwnd = CreateWindowExW(0, L"WebcamWin", L"Phone Webcam - Decoding...", WS_OVERLAPPEDWINDOW | WS_VISIBLE, 100, 100, 1300, 760, NULL, NULL, GetModuleHandle(NULL), NULL);
    std::thread v(videoThread, 5005);
    SOCKET ctrl = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    sockaddr_in caddr = { AF_INET, htons(8080) }; inet_pton(AF_INET, ip, &caddr.sin_addr.s_addr);
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
