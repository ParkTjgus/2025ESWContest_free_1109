import { PermissionsAndroid, Platform } from "react-native";

class PermissionService {
  public async requestBluetoothPermissions(): Promise<boolean> {
    if (Platform.OS === "ios") {
      // iOS는 별도의 스캔/연결 권한 요청이 필요 없음 (Info.plist로 처리)
      return true;
    }

    // Android 전용 로직
    const apiLevel = parseInt(Platform.Version.toString(), 10);
    if (apiLevel < 31) {
      const granted = await PermissionsAndroid.request(
        PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION
      );
      return granted === PermissionsAndroid.RESULTS.GRANTED;
    } else {
      const result = await PermissionsAndroid.requestMultiple([
        PermissionsAndroid.PERMISSIONS.BLUETOOTH_SCAN,
        PermissionsAndroid.PERMISSIONS.BLUETOOTH_CONNECT,
      ]);
      return (
        result["android.permission.BLUETOOTH_SCAN"] === "granted" &&
        result["android.permission.BLUETOOTH_CONNECT"] === "granted"
      );
    }
  }
}

// 싱글턴(Singleton) 인스턴스로 export
export const permissionService = new PermissionService();
