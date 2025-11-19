import { NativeModules } from 'react-native';

const { VideoGeneratorModule } = NativeModules;

export const generateVideo = async (
    audioUri: string,
    imageUri: string,
    startTime: number = 0,
    duration: number | null = null
): Promise<string | null> => {
    if (!VideoGeneratorModule) {
        console.warn("VideoGeneratorModule is not available.");
        return null;
    }

    // Default duration if not provided (e.g., 5 seconds)
    const videoDuration = duration ? duration * 1000 : 5000;

    // Sanitize paths for Native Module (remove file:// prefix)
    const cleanAudioUri = audioUri.replace('file://', '');
    const cleanImageUri = imageUri.replace('file://', '');

    // Output path
    const outputUri = `${cleanImageUri.substring(0, cleanImageUri.lastIndexOf('/'))}/output_${Date.now()}.mp4`;

    try {
        console.log("Starting video generation...");
        const result = await VideoGeneratorModule.generateVideo(cleanImageUri, cleanAudioUri, outputUri, videoDuration);
        console.log("Video generation successful:", result);
        return result;
    } catch (error) {
        console.error("Video generation failed:", error);
        return null;
    }
};
