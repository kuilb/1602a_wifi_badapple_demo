import java.io.*;

public class FileDecoder {

    public static void main(String[] args) throws Exception {
        FileInputStream in = new FileInputStream("output.bin");

        byte[] frameData = new byte[64];
        byte[] separator = new byte[1];

        while (true) {
            int timestamp = readInt(in);
            int frameNumber = readInt(in);

            int bytesRead = in.read(frameData);
            if (bytesRead < 64) break;

            in.read(separator); // 跳过结束标志

            // 解码并显示帧
            boolean[][] pixels = decodeFrame(frameData);
            printFrame(pixels);

            // 控制帧率显示
            Thread.sleep(33); // 约30 FPS
        }

        in.close();
    }

    private static int readInt(InputStream in) throws IOException {
        return (in.read() << 24) | (in.read() << 16) | (in.read() << 8) | in.read();
    }

    // 解码：将64字节转为23×17像素布尔矩阵
    private static boolean[][] decodeFrame(byte[] frameData) {
        boolean[][] pixels = new boolean[17][23];

        for (int block = 0; block < 8; block++) {
            int blockX = block % 4;
            int blockY = block / 4;

            // 映射块顺序（解码需反映编码中的 xMap）
            int[] xMap = {0,1,2,3}; // 编码是 4 1 2 3 → 映射为 3 0 1 2
            int realX = xMap[blockX];

            int startX = realX * 6;
            int startY = blockY * 8;

            for (int row = 0; row < 8; row++) {
                int value = frameData[block * 8 + row] & 0xFF;
                for (int col = 0; col < 5; col++) {
                    int bit = (value >> (4 - col)) & 1;
                    int px = startX + col;
                    int py = startY + row;
                    if (px < 23 && py < 17) {
                        pixels[py][px] = bit == 1;
                    }
                }
            }
        }

        return pixels;
    }

    private static void printFrame(boolean[][] pixels) {
        System.out.print("\033[H\033[2J"); // 清屏
        for (boolean[] row : pixels) {
            for (boolean pixel : row) {
                System.out.print(pixel ? '█' : ' ');
            }
            System.out.println();
        }
    }
}
