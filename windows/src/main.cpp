#include <iostream>
#include <winsock2.h>
#include <ws2tcpip.h>
#include <vector>
#include <string>

#pragma comment(lib, "ws2_32.lib")

void handleControlChannel(const char* ip, int port) {
    SOCKET connectSocket = INVALID_SOCKET;
    struct sockaddr_in clientService;

    connectSocket = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (connectSocket == INVALID_SOCKET) {
        std::cerr << "socket failed with error: " << WSAGetLastError() << std::endl;
        return;
    }

    clientService.sin_family = AF_INET;
    inet_pton(AF_INET, ip, &clientService.sin_addr.s_addr);
    clientService.sin_port = htons(port);

    std::cout << "Connecting to " << ip << ":" << port << "..." << std::endl;

    if (connect(connectSocket, (SOCKADDR*)&clientService, sizeof(clientService)) == SOCKET_ERROR) {
        std::cerr << "connect failed with error: " << WSAGetLastError() << std::endl;
        closesocket(connectSocket);
        return;
    }

    std::cout << "Connected to phone control channel." << std::endl;

    // Read 4-byte length
    uint32_t length = 0;
    int bytesReceived = recv(connectSocket, (char*)&length, 4, 0);
    if (bytesReceived == 4) {
        length = ntohl(length); // Big-endian to host
        std::cout << "Expecting JSON payload of " << length << " bytes" << std::endl;

        std::vector<char> buffer(length + 1);
        bytesReceived = recv(connectSocket, buffer.data(), length, 0);
        if (bytesReceived > 0) {
            buffer[bytesReceived] = '\0';
            std::cout << "Received: " << buffer.data() << std::endl;
        }
    }

    // Keep alive for a bit
    Sleep(5000);

    closesocket(connectSocket);
}

int main(int argc, char** argv) {
    const char* targetIp = "127.0.0.1";
    int targetPort = 8080;

    if (argc > 1) targetIp = argv[1];

    WSADATA wsaData;
    if (WSAStartup(MAKEWORD(2, 2), &wsaData) != 0) {
        return 1;
    }

    handleControlChannel(targetIp, targetPort);

    WSACleanup();
    return 0;
}
