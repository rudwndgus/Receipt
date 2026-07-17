# Receipt Sketch

Wintec AnyPOS80 (Android 7.1)에서 손가락으로 그린 그림을 내장 80mm 영수증 프린터로 보내는 최소 구성 네이티브 Android 앱입니다.

## 사용

1. 앱을 열고 흰 화면에 그립니다.
2. `PEN`과 `ERASER`로 그리거나 지웁니다.
3. `CLEAR`는 확인 후 전체 그림을 지웁니다.
4. `PRINT`는 그림 영역을 잘라 576px 흑백 ESC/POS 래스터로 출력하고 4줄 배출 후 커팅 명령을 보냅니다.

USB 프린터 권한이 아직 없으면 Android 시스템이 최초 1회 권한을 요청할 수 있습니다. 권한을 승인하면 같은 버튼 동작에서 출력이 이어집니다.

## 프린터 연결

확인되지 않은 Wintec API 이름을 추측하지 않았습니다. 백엔드는 다음 순서로 실제 사용 가능한 연결만 선택합니다.

1. 검증된 Wintec SDK 백엔드 (현재 SDK가 없어 비활성)
2. Android USB Host가 프린터 클래스(USB class 7)로 노출한 ESC/POS 장치
3. 앱 프로세스가 실제로 쓸 수 있는 `/dev/usb/lp0` 프린터 장치 또는 ADB로 확인 후 `SerialEscPosPrinterBackend.CONFIGURED_SERIAL_PATH`에 지정한 serial 장치

확인 없이 `/dev/ttyS*`를 추측하면 스캐너나 외부 COM 포트에 출력 데이터를 보낼 수 있으므로 사용하지 않습니다. 이 AnyPOS80 펌웨어가 프린터를 전용 서비스나 vendor-specific USB 인터페이스로만 노출한다면 제조사 SDK/AIDL 또는 기기에서 추출한 데모가 추가로 필요합니다. 그 경우 `WintecPrinterBackend`만 실제 API에 맞게 교체하면 됩니다.

## 빌드

Android Studio에서 프로젝트를 열고 `app`을 빌드하거나 다음 명령을 실행합니다.

```text
gradlew.bat assembleDebug
```

결과 APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 진단 로그

Logcat 태그: `ReceiptSketch`, `ReceiptSketch-Printer`, `ReceiptSketch-Wintec`, `ReceiptSketch-USB`, `ReceiptSketch-Serial`
