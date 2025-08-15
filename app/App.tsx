import React, { useEffect } from "react";
import { View, Text, Button, StyleSheet, SafeAreaView } from "react-native";

// Navigation 라이브러리 import
import { NavigationContainer } from "@react-navigation/native";
import {
  createNativeStackNavigator,
  NativeStackScreenProps,
} from "@react-navigation/native-stack";

// 우리가 만든 스토어와 화면 import
import { useBleStore } from "./src/stores/useBleStore"; // 스토어 경로 확인
import BluetoothScreen from "./src/screens/BluetoothScreen"; // 블루투스 화면 경로 확인

/**
 * 네비게이터가 관리할 화면 목록과 파라미터 타입을 정의합니다.
 */
type RootStackParamList = {
  Home: undefined;
  Bluetooth: undefined;
};

/**
 * HomeScreen 컴포넌트가 받을 props 타입을 정의합니다.
 */
type HomeScreenProps = NativeStackScreenProps<RootStackParamList, "Home">;

/**
 * 첫 번째 화면 (홈 화면)
 */
const HomeScreen: React.FC<HomeScreenProps> = ({ navigation }) => {
  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.content}>
        <Text style={styles.title}>블루투스 테스트 앱</Text>
        <Button
          title="블루투스 연결 화면으로 이동"
          onPress={() => navigation.navigate("Bluetooth")}
        />
      </View>
    </SafeAreaView>
  );
};

// 네비게이션 스택 생성 (ParamList 타입을 전달)
const Stack = createNativeStackNavigator<RootStackParamList>();

/**
 * 앱의 메인 컴포넌트
 */
const App = () => {
  // 스토어에서 initialize와 cleanup 함수를 가져옵니다.
  const { initialize, cleanup } = useBleStore.getState();

  // useEffect를 사용해 앱의 생명주기에 맞춰 BLE 서비스를 관리합니다.
  useEffect(() => {
    // 앱이 시작(마운트)될 때 BLE 서비스를 초기화합니다.
    initialize();

    // 앱이 종료(언마운트)될 때 리스너 등을 정리합니다.
    return () => {
      cleanup();
    };
  }, [initialize, cleanup]);

  return (
    <NavigationContainer>
      <Stack.Navigator initialRouteName="Home">
        <Stack.Screen
          name="Home"
          component={HomeScreen}
          options={{ title: "홈" }}
        />
        <Stack.Screen
          name="Bluetooth"
          component={BluetoothScreen}
          options={{ title: "기기 연결" }}
        />
      </Stack.Navigator>
    </NavigationContainer>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  content: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
  },
  title: {
    fontSize: 24,
    fontWeight: "bold",
    marginBottom: 20,
  },
});

export default App;
