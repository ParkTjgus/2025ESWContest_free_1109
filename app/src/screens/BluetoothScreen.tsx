import React, { useEffect } from "react";
import {
  View,
  Text,
  Button,
  FlatList,
  StyleSheet,
  TouchableOpacity,
  ActivityIndicator,
  SafeAreaView,
} from "react-native";
import { useBleStore } from "../stores/useBleStore";
import { Peripheral } from "react-native-ble-manager";

const BluetoothScreen = () => {
  // 1. 스토어에서 상태를 구독합니다.
  //    렌더링에 필요한 상태들만 선택(select)하여 불필요한 리렌더링을 방지합니다.
  const status = useBleStore((s) => s.status);
  const devices = useBleStore((s) => s.devices);
  const connectedDevice = useBleStore((s) => s.connectedDevice);
  const error = useBleStore((s) => s.error);

  // 2. 스토어의 액션 함수들을 가져옵니다.
  //    액션 함수는 렌더링마다 새로 가져올 필요 없으므로 getState()를 사용합니다.
  const { startScan, connectToDevice, disconnectDevice } =
    useBleStore.getState();

  // 목록의 각 항목을 렌더링하는 함수
  const renderItem = ({ item }: { item: Peripheral }) => (
    <TouchableOpacity
      style={styles.deviceItem}
      onPress={() => connectToDevice(item)}
    >
      <Text style={styles.deviceName}>{item.name || "Unknown Device"}</Text>
      <Text style={styles.deviceId}>{item.id}</Text>
    </TouchableOpacity>
  );

  // 현재 상태에 맞는 텍스트를 반환하는 헬퍼 함수
  const getStatusText = () => {
    switch (status) {
      case "scanning":
        return "주변 기기를 찾고 있습니다...";
      case "connecting":
        return `기기에 연결 중입니다...`;
      case "connected":
        return `✅ ${
          connectedDevice?.name || connectedDevice?.id
        }에 연결되었습니다.`;
      case "error":
        return `❌ 오류: ${error}`;
      default:
        return "시작 버튼을 눌러 블루투스 기기를 찾아보세요.";
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      {/* --- 상단 상태 표시 영역 --- */}
      <View style={styles.header}>
        <Text style={styles.statusText}>{getStatusText()}</Text>
        {(status === "scanning" || status === "connecting") && (
          <ActivityIndicator color="#007BFF" />
        )}
      </View>

      {/* --- 중앙 액션 버튼 영역 --- */}
      <View style={styles.actionZone}>
        {connectedDevice ? (
          <Button
            title="연결 해제"
            onPress={disconnectDevice}
            color="#E53935"
          />
        ) : (
          <Button
            title="기기 스캔"
            onPress={startScan}
            disabled={status === "scanning"}
          />
        )}
      </View>

      {/* --- 기기 목록 영역 --- */}
      <FlatList
        data={devices}
        renderItem={renderItem}
        keyExtractor={(item) => item.id}
        style={styles.list}
        ListEmptyComponent={
          status !== "scanning" ? (
            <Text style={styles.emptyText}>발견된 기기가 없습니다.</Text>
          ) : null
        }
      />
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#F5F5F5",
  },
  header: {
    padding: 20,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    minHeight: 60,
  },
  statusText: {
    fontSize: 18,
    textAlign: "center",
    marginRight: 10,
    color: "#333",
  },
  actionZone: {
    paddingHorizontal: 20,
    marginBottom: 20,
  },
  list: {
    flex: 1,
    paddingHorizontal: 20,
  },
  deviceItem: {
    padding: 15,
    marginBottom: 10,
    backgroundColor: "white",
    borderRadius: 8,
    elevation: 2,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.2,
    shadowRadius: 1.41,
  },
  deviceName: {
    fontSize: 16,
    fontWeight: "bold",
  },
  deviceId: {
    fontSize: 12,
    color: "gray",
    marginTop: 4,
  },
  emptyText: {
    textAlign: "center",
    marginTop: 50,
    color: "gray",
    fontSize: 16,
  },
});

export default BluetoothScreen;
