import { PermissionsAndroid } from "react-native";
import { permissionService } from "../services/PermissionService"; // 실제 파일 경로에 맞게 수정하세요.

// PermissionsAndroid 모듈을 모킹(mocking)합니다.
jest.mock("react-native", () => ({
  // Platform, PermissionsAndroid 등 필요한 모듈만 모킹
  PermissionsAndroid: {
    request: jest.fn(), // request 함수를 가짜 함수로 대체
    PERMISSIONS: {
      BLUETOOTH_SCAN: "android.permission.BLUETOOTH_SCAN",
      BLUETOOTH_CONNECT: "android.permission.BLUETOOTH_CONNECT",
    },
    RESULTS: {
      GRANTED: "granted",
    },
  },
}));

// PermissionsAndroid.request를 jest.Mock 타입으로 캐스팅하여 타입스크립트 지원을 받습니다.
const mockedRequest = PermissionsAndroid.request as jest.Mock;

describe("PermissionService", () => {
  // 각 테스트 실행 전에 모킹된 함수의 호출 기록을 초기화합니다.
  beforeEach(() => {
    mockedRequest.mockClear();
  });

  // --- requestScanPermission 테스트 스위트 ---
  describe("requestScanPermission", () => {
    it("BLUETOOTH_SCAN 권한이 승인되면 true를 반환해야 합니다", async () => {
      // 권한 요청 결과를 'granted'로 설정
      mockedRequest.mockResolvedValue(PermissionsAndroid.RESULTS.GRANTED);

      const result = await permissionService.requestScanPermission();

      // 결과가 true인지 확인
      expect(result).toBe(true);
      // request 함수가 올바른 권한으로 호출되었는지 확인
      expect(mockedRequest).toHaveBeenCalledWith(
        PermissionsAndroid.PERMISSIONS.BLUETOOTH_SCAN
      );
    });

    it("BLUETOOTH_SCAN 권한이 거부되면 false를 반환해야 합니다", async () => {
      // 권한 요청 결과를 'denied' (granted가 아닌 아무 값)으로 설정
      mockedRequest.mockResolvedValue("denied");

      const result = await permissionService.requestScanPermission();

      // 결과가 false인지 확인
      expect(result).toBe(false);
    });
  });

  // --- requestConnectPermission 테스트 스위트 ---
  describe("requestConnectPermission", () => {
    it("BLUETOOTH_CONNECT 권한이 승인되면 true를 반환해야 합니다", async () => {
      mockedRequest.mockResolvedValue(PermissionsAndroid.RESULTS.GRANTED);

      const result = await permissionService.requestConnectPermission();

      expect(result).toBe(true);
      expect(mockedRequest).toHaveBeenCalledWith(
        PermissionsAndroid.PERMISSIONS.BLUETOOTH_CONNECT
      );
    });

    it("BLUETOOTH_CONNECT 권한이 거부되면 false를 반환해야 합니다", async () => {
      mockedRequest.mockResolvedValue("denied");

      const result = await permissionService.requestConnectPermission();

      expect(result).toBe(false);
    });
  });
});
