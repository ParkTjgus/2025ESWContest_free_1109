import { PermissionsAndroid } from "react-native";

class PermissionService {
  /**
   * 블루투스 스캔에 필요한 권한을 요청
   */
  public async requestScanPermission(): Promise<boolean> {
    const granted = await PermissionsAndroid.request(
      PermissionsAndroid.PERMISSIONS.BLUETOOTH_SCAN
    );
    return granted === PermissionsAndroid.RESULTS.GRANTED;
  }

  /**
   * 블루투스 연결에 필요한 권한을 요청
   */
  public async requestConnectPermission(): Promise<boolean> {
    const granted = await PermissionsAndroid.request(
      PermissionsAndroid.PERMISSIONS.BLUETOOTH_CONNECT
    );
    return granted === PermissionsAndroid.RESULTS.GRANTED;
  }

  // PermissionService에 추가할 메서드
  public async requestLocationPermission(): Promise<boolean> {
    const granted = await PermissionsAndroid.request(
      PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION
    );
    return granted === PermissionsAndroid.RESULTS.GRANTED;
  }
}

export const permissionService = new PermissionService();
