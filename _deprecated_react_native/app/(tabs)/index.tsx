import Waveform from '@/components/Waveform';
import { getToken } from '@/lib/auth';
import { fetchUserProfile, UserProfile } from '@/lib/x-api';
import FontAwesome from '@expo/vector-icons/FontAwesome';
import { Audio, InterruptionModeAndroid, InterruptionModeIOS } from 'expo-av';
import { useRouter } from 'expo-router';
import { useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Image, Modal, Pressable, Text, TouchableOpacity, View } from 'react-native';

const RECORDING_OPTIONS: Audio.RecordingOptions = {
  ...Audio.RecordingOptionsPresets.HIGH_QUALITY,
  isMeteringEnabled: true,
};

export default function TabOneScreen() {
  const [recording, setRecording] = useState<Audio.Recording | null>(null);
  const [permissionResponse, requestPermission] = Audio.usePermissions();
  const [user, setUser] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [lastRecordingUri, setLastRecordingUri] = useState<string | null>(null);
  const [metering, setMetering] = useState(-160);
  const [statusMessage, setStatusMessage] = useState('');
  const [isStopping, setIsStopping] = useState(false);
  const [showStopModal, setShowStopModal] = useState(false);

  const recordingRef = useRef<Audio.Recording | null>(null);
  const stoppingRef = useRef(false);

  const router = useRouter();

  useEffect(() => {
    checkAuth();
  }, []);

  const checkAuth = async () => {
    const token = await getToken();
    if (!token) {
      router.replace('/auth');
      return;
    }
    try {
      const profile = await fetchUserProfile();
      setUser(profile);
    } catch (error) {
      console.error('Failed to fetch profile', error);
      router.replace('/auth');
    } finally {
      setLoading(false);
    }
  };

  const finalizeRecording = useCallback(
    async (options: { durationOverride?: number } = {}) => {
      const activeRecording = recordingRef.current;
      if (!activeRecording || stoppingRef.current) return;

      stoppingRef.current = true;
      setIsStopping(true);
      setStatusMessage('Stopping recording...');
      try {
        const statusBefore = await activeRecording.getStatusAsync();
        if (statusBefore.isRecording) {
          await activeRecording.stopAndUnloadAsync();
        }

        await Audio.setAudioModeAsync({
          allowsRecordingIOS: false,
        });

        const uri = activeRecording.getURI();
        const statusAfter = await activeRecording.getStatusAsync();
        const duration =
          options.durationOverride ??
          statusAfter.durationMillis ??
          statusBefore.durationMillis ??
          0;

        recordingRef.current = null;
        setRecording(null);
        setMetering(-160);
        setStatusMessage('');

        if (uri && duration > 0) {
          router.push({
            pathname: '/preview',
            params: {
              uri: encodeURIComponent(uri),
              duration: String(duration),
            },
          });
        } else {
          setStatusMessage('Recording did not save correctly');
        }

        setLastRecordingUri(uri);
        console.log('Recording stopped and stored at', uri);
      } catch (error) {
        console.error('Failed to stop recording', error);
        setStatusMessage('Unable to stop recording');
      } finally {
        stoppingRef.current = false;
        setIsStopping(false);
      }
    },
    [router]
  );

  const handleRecordingStatus = useCallback(
    (status: Audio.RecordingStatus) => {
      if (typeof status.metering === 'number') {
        setMetering(status.metering);
      }

      if (
        recordingRef.current &&
        !status.isRecording &&
        !stoppingRef.current &&
        (status.durationMillis ?? 0) > 0
      ) {
        finalizeRecording({ durationOverride: status.durationMillis ?? undefined });
      }
    },
    [finalizeRecording]
  );

  async function startRecording() {
    try {
      if (permissionResponse?.status !== 'granted') {
        const response = await requestPermission();
        if (response?.status !== 'granted') {
          setStatusMessage('Microphone permission is required');
          return;
        }
      }
      await Audio.setAudioModeAsync({
        allowsRecordingIOS: true,
        playsInSilentModeIOS: true,
        staysActiveInBackground: false,
        interruptionModeIOS: InterruptionModeIOS.DoNotMix,
        interruptionModeAndroid: InterruptionModeAndroid.DoNotMix,
        shouldDuckAndroid: true,
        playThroughEarpieceAndroid: false,
      });

      setLastRecordingUri(null);
      console.log('Starting recording..');

      const recording = new Audio.Recording();
      try {
        await recording.prepareToRecordAsync(RECORDING_OPTIONS);
        recording.setOnRecordingStatusUpdate(handleRecordingStatus);
        recording.setProgressUpdateInterval(200);
        await recording.startAsync();

        recordingRef.current = recording;
        setRecording(recording);
        console.log('Recording started');
      } catch (error) {
        console.error('Failed to prepare or start recording', error);
        setStatusMessage('Unable to start recording');
      }
    } catch (err) {
      console.error('Failed to setup recording session', err);
      setStatusMessage('Unable to start recording');
    }
  }

  const requestStopRecording = () => {
    if (!recordingRef.current || isStopping) {
      return;
    }
    setShowStopModal(true);
  };

  return (
    <View className="flex-1 items-center justify-center bg-black p-4">
      {loading ? (
        <ActivityIndicator size="large" color="#1DA1F2" />
      ) : (
        <>
          {user && (
            <View className="absolute top-12 right-4 flex-row items-center">
              <Image
                source={{ uri: user.profile_image_url.replace('_normal', '') }}
                className="w-10 h-10 rounded-full mr-2"
              />
              <Text className="text-white font-bold">{user.name}</Text>
            </View>
          )}

          <View className="h-32 w-full items-center justify-center mb-10">
            {recording ? (
              <Waveform metering={metering} />
            ) : (
              <Text className="text-x-gray">Tap mic to record</Text>
            )}
          </View>

          {statusMessage ? <Text className="text-white mb-4">{statusMessage}</Text> : null}

          <TouchableOpacity
            onPress={recording ? requestStopRecording : startRecording}
            disabled={isStopping}
            className={`w-20 h-20 rounded-full items-center justify-center ${recording ? 'bg-red-500' : 'bg-x-blue'
              } ${isStopping ? 'opacity-50' : ''}`}
          >
            <FontAwesome name={recording ? 'stop' : 'microphone'} size={32} color="white" />
          </TouchableOpacity>

          <Text className="text-x-gray mt-4 mb-8">
            {recording ? 'Recording...' : 'Tap to Record'}
          </Text>

          {!recording && lastRecordingUri && (
            <Text className="text-white mt-4">Preview ready. Tap stop sooner to re-record.</Text>
          )}

          <Modal
            animationType="fade"
            transparent={true}
            visible={showStopModal}
            onRequestClose={() => setShowStopModal(false)}
          >
            <View className="flex-1 justify-center items-center bg-black/80">
              <View className="bg-gray-900 p-6 rounded-2xl w-4/5 items-center border border-gray-800">
                <Text className="text-white text-xl font-bold mb-2">Stop recording?</Text>
                <Text className="text-gray-400 mb-6 text-center">We will stop and take you to preview before posting.</Text>

                <Pressable
                  className="bg-red-500 w-full p-4 rounded-full mb-3 items-center active:opacity-80"
                  onPress={() => {
                    setShowStopModal(false);
                    finalizeRecording();
                  }}
                >
                  <Text className="text-white font-bold">Stop & Preview</Text>
                </Pressable>

                <Pressable
                  className="w-full p-4 rounded-full items-center bg-gray-800 active:opacity-80"
                  onPress={() => setShowStopModal(false)}
                >
                  <Text className="text-white font-bold">Keep Recording</Text>
                </Pressable>
              </View>
            </View>
          </Modal>
        </>
      )}
    </View>
  );
}
