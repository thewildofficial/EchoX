import React, { useEffect } from 'react';
import { StyleSheet, View } from 'react-native';
import Animated, { Easing, useAnimatedProps, useSharedValue, withTiming } from 'react-native-reanimated';
import Svg, { Line } from 'react-native-svg';

const AnimatedLine = Animated.createAnimatedComponent(Line);

interface WaveformProps {
    metering: number; // Current decibel level (-160 to 0)
}

const NUM_BARS = 20;

const WaveformBar = ({ index, metering }: { index: number; metering: number }) => {
    const height = useSharedValue(10);

    useEffect(() => {
        // Normalize metering: -160 (silence) to 0 (loud) -> 0 to 1
        // Typical speech is around -30 to -10
        const normalized = Math.max(0, (metering + 60) / 60); // Clip below -60dB

        const randomFactor = Math.random() * 0.5 + 0.5;
        // Center bars are taller
        const centerFactor = 1 - Math.abs(index - NUM_BARS / 2) / (NUM_BARS / 2);
        const targetHeight = Math.max(5, normalized * 50 * randomFactor * (0.5 + centerFactor));

        height.value = withTiming(targetHeight, {
            duration: 180, // Slightly less than update interval
            easing: Easing.linear,
        });
    }, [metering, index, height]);

    const animatedProps = useAnimatedProps(() => ({
        y1: 30 - height.value / 2,
        y2: 30 + height.value / 2,
    }));

    const x = index * 10 + 5;

    return (
        <AnimatedLine
            x1={x}
            x2={x}
            stroke="#1d9bf0"
            strokeWidth="4"
            strokeLinecap="round"
            animatedProps={animatedProps}
        />
    );
};

export default function Waveform({ metering }: WaveformProps) {
    return (
        <View style={styles.container}>
            <Svg height="60" width="200">
                {Array.from({ length: NUM_BARS }).map((_, index) => (
                    <WaveformBar key={index} index={index} metering={metering} />
                ))}
            </Svg>
        </View>
    );
}

const styles = StyleSheet.create({
    container: {
        alignItems: 'center',
        justifyContent: 'center',
    },
});
