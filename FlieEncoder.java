import java.io.*;

public class FlieEncoder {

    public static void main(String[] args) {
        String videoFile = "badapple.mp4";  // 换成文件路径
        int fps = 0;

        // 先用 ffprobe 获取视频帧率，失败则默认30fps
        try {
            fps = getVideoFps(videoFile);
            System.out.println("Detected FPS: " + fps);
        } catch (Exception e) {
            System.err.println("Failed to get FPS, defaulting to 30");
            fps = 30;
        }

        // 启动 ffmpeg 进程
        ProcessBuilder pb = new ProcessBuilder(
            "ffmpeg",
            "-i", videoFile,

            "-vf", "colorchannelmixer=.299:.587:.114:0:.299:.587:.114:0:.299:.587:.114,scale=23:17:flags=lanczos",
            "-pix_fmt", "gray",     // 灰度像素格式
            "-f", "rawvideo",       // 原始视频格式输出（无容器）
            "-fflags", "+bitexact",
            "-loglevel", "quiet",
            "-nostdin",
            "-"                     // 输出到标准输出流
        );

        pb.redirectErrorStream(true); // 将 stderr 合并到 stdout，方便调试

        try {
            Process process = pb.start();
            InputStream ffmpegOut = process.getInputStream();   // 读取ffmpeg输出的视频帧字节流
            FileOutputStream outFile = new FileOutputStream("output.bin"); // 输出编码后的文件

            byte[] frame = new byte[23 * 17];   // 每帧灰度图像数据（宽*高）
            byte[] encoded = new byte[64];      // 编码后的数据缓存区
            int frameNumber = 0;

            // 第一次先跳过 276 字节
            //ffmpegOut.skip(276);

            while (true) {
                // 读取一帧数据，直到读满23*17个字节
                int bytesRead = 0;
                while (bytesRead < frame.length) {
                    int read = ffmpegOut.read(frame, bytesRead, frame.length - bytesRead);
                    if (read == -1) break; // EOF 读到流尾，退出
                    bytesRead += read;
                }
                if (bytesRead < frame.length) break;    // 不足一帧，结束循环

                // 编码帧
                encodeFrame(frame, encoded);

                // 使用帧编号计算时间戳，单位毫秒
                long timestamp = frameNumber * 1000L / fps;

                // 写入：时间戳 + 帧编号 + 数据 + 结束标志
                writeInt(outFile, (int) timestamp);
                writeInt(outFile, frameNumber++);
                outFile.write(encoded);
                outFile.write(0xFF); // 结束标志
            }

            // 关闭资源，等待ffmpeg进程结束
            outFile.close();
            ffmpegOut.close();
            process.waitFor();

            System.out.println("Done!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    
    // 将输入字节按阈值转成0或1，用于二值化像素
    private static int bit(byte value) {
        return (value & 0xFF) > 127 ? 1 : 0;
    }

    /*
     *  计算指定子区域在整张23x17帧中的索引位置
     *  x 大块的x坐标(0~3)
     *  y 大块的y坐标(0~1)
     *  dx 大块内像素x坐标(0~5)
     *  dy 大块内像素y坐标(0~8)
     *  return 对应像素在frame数组中的索引
     */
    private static int index(int x, int y, int dx, int dy) {
        int baseX = 6 * x + dx;           // 每块宽5+1分隔像素
        int baseY = 8 * y + dy;           // 每块高8行
        // System.out.println("orgin baseX: " + baseX + "  baseY: " + baseY);

        // System.out.println("trans baseX: " + baseX + "  baseY: " + baseY);

        return baseY * 23 + baseX;
    }

    /*
     * 将帧数据编码为 8 字节，每字节代表 1 行的 5 个像素，低 5 位编码（bit0 ~ bit4）
     * 总共编码一个 5X8 像素的区域
     * 传入的frame 23x17=391个 一个frame = 一个原始像素
     * 输出的out (一个块8个字节) 8*（8个块）2*4=64个
     * 一个out = 1行
     */
    private static void encodeFrame(byte[] frame, byte[] out) {
        for(int y = 0; y < 2 ; y++){
            for(int x = 0; x < 4; x++){
                int blockIndex = y * 4 + x;
                // 一维化后的块编号
                int i = blockIndex * 8;
                // 每个块8行

                //System.out.println("y: " + y + " " + "x: " + x);
                // System.out.println(i);
                //一个块中的8行
                out[i + 0] = (byte) (
                    //块中第1行的5个像素
                    (bit(frame[index(x, y, 4, 0)]) << 0) |
                    (bit(frame[index(x, y, 3, 0)]) << 1) |
                    (bit(frame[index(x, y, 2, 0)]) << 2) |
                    (bit(frame[index(x, y, 1, 0)]) << 3) |
                    (bit(frame[index(x, y, 0, 0)]) << 4) 
                );

                // System.out.println(index(x, y, 4, 0));
                // System.out.println(index(x, y, 3, 0));
                // System.out.println(index(x, y, 2, 0));
                // System.out.println(index(x, y, 1, 0));
                // System.out.println(index(x, y, 0, 0));
                // //System.out.println(String.format("%8s", Integer.toBinaryString(out[i] & 0xFF)).replace(' ', '0'));
                // System.out.println();

                out[i + 1] = (byte) (
                    //块中第2行的5个像素
                    (bit(frame[index(x, y, 4, 1)]) << 0) |
                    (bit(frame[index(x, y, 3, 1)]) << 1) |
                    (bit(frame[index(x, y, 2, 1)]) << 2) |
                    (bit(frame[index(x, y, 1, 1)]) << 3) |
                    (bit(frame[index(x, y, 0, 1)]) << 4) 
                );

                out[i + 2] = (byte) (
                    //块中第3行的5个像素
                    (bit(frame[index(x, y, 4, 2)]) << 0) |
                    (bit(frame[index(x, y, 3, 2)]) << 1) |
                    (bit(frame[index(x, y, 2, 2)]) << 2) |
                    (bit(frame[index(x, y, 1, 2)]) << 3) |
                    (bit(frame[index(x, y, 0, 2)]) << 4) 
                );

                out[i + 3] = (byte) (
                    //块中第4行的5个像素
                    (bit(frame[index(x, y, 4, 3)]) << 0) |
                    (bit(frame[index(x, y, 3, 3)]) << 1) |
                    (bit(frame[index(x, y, 2, 3)]) << 2) |
                    (bit(frame[index(x, y, 1, 3)]) << 3) |
                    (bit(frame[index(x, y, 0, 3)]) << 4) 
                );

                out[i + 4] = (byte) (
                    //块中第5行的5个像素
                    (bit(frame[index(x, y, 4, 4)]) << 0) |
                    (bit(frame[index(x, y, 3, 4)]) << 1) |
                    (bit(frame[index(x, y, 2, 4)]) << 2) |
                    (bit(frame[index(x, y, 1, 4)]) << 3) |
                    (bit(frame[index(x, y, 0, 4)]) << 4) 
                );

                out[i + 5] = (byte) (
                    //块中第6行的5个像素
                    (bit(frame[index(x, y, 4, 5)]) << 0) |
                    (bit(frame[index(x, y, 3, 5)]) << 1) |
                    (bit(frame[index(x, y, 2, 5)]) << 2) |
                    (bit(frame[index(x, y, 1, 5)]) << 3) |
                    (bit(frame[index(x, y, 0, 5)]) << 4) 
                );

                out[i + 6] = (byte) (
                    //块中第7行的5个像素
                    (bit(frame[index(x, y, 4, 6)]) << 0) |
                    (bit(frame[index(x, y, 3, 6)]) << 1) |
                    (bit(frame[index(x, y, 2, 6)]) << 2) |
                    (bit(frame[index(x, y, 1, 6)]) << 3) |
                    (bit(frame[index(x, y, 0, 6)]) << 4) 
                );

                out[i + 7] = (byte) (
                    //块中第8行的5个像素
                    (bit(frame[index(x, y, 4, 7)]) << 0) |
                    (bit(frame[index(x, y, 3, 7)]) << 1) |
                    (bit(frame[index(x, y, 2, 7)]) << 2) |
                    (bit(frame[index(x, y, 1, 7)]) << 3) |
                    (bit(frame[index(x, y, 0, 7)]) << 4) 
                );

                // System.out.println(String.format("%8s", Integer.toBinaryString(out[i] & 0xFF)).replace(' ', '0'));
                // System.out.println(String.format("%8s", Integer.toBinaryString(out[i+1] & 0xFF)).replace(' ', '0'));
                // System.out.println(String.format("%8s", Integer.toBinaryString(out[i+2] & 0xFF)).replace(' ', '0'));
                // System.out.println(String.format("%8s", Integer.toBinaryString(out[i+3] & 0xFF)).replace(' ', '0'));
                // System.out.println(String.format("%8s", Integer.toBinaryString(out[i+4] & 0xFF)).replace(' ', '0'));
                // System.out.println(String.format("%8s", Integer.toBinaryString(out[i+5] & 0xFF)).replace(' ', '0'));
                // System.out.println(String.format("%8s", Integer.toBinaryString(out[i+6] & 0xFF)).replace(' ', '0'));
                // System.out.println(String.format("%8s", Integer.toBinaryString(out[i+7] & 0xFF)).replace(' ', '0'));
                // System.out.println();
            }
        }
    }

    
    private static void writeInt(OutputStream out, int value) throws IOException {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write((value) & 0xFF);
    }

    // 调用ffprobe获取整数fps
    private static int getVideoFps(String videoFilePath) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
            "ffprobe",
            "-v", "0",
            "-select_streams", "v:0",
            "-show_entries", "stream=r_frame_rate",
            "-of", "default=noprint_wrappers=1:nokey=1",
            videoFilePath
        );
        Process process = pb.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String fpsStr = reader.readLine();
        process.waitFor();

        if (fpsStr == null || fpsStr.isEmpty()) {
            throw new RuntimeException("Failed to get FPS from ffprobe.");
        }

        String[] parts = fpsStr.trim().split("/");
        double fps;
        if (parts.length == 2) {
            fps = Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]);
        } else {
            fps = Double.parseDouble(parts[0]);
        }

        // 转为整数fps（向下取整）
        return (int) fps;
    }
}
