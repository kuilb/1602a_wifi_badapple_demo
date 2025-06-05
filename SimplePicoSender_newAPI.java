import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class SimplePicoSender_newAPI {
    public static void main(String[] args) {
        File file = new File("output.bin");

        try (
            Socket socket = new Socket("192.168.8.125", 13000);
            FileInputStream inFile = new FileInputStream(file)
        ) {
            socket.setSoTimeout(3000);
            OutputStream out = socket.getOutputStream();

            byte[] buffer = new byte[73]; // 4(timestamp) + 4(frameNo) + 64(data) + 1(endFlag)
            int frameCount = 0;

            while (inFile.read(buffer) == 73) {
                int timestamp = toInt(buffer, 0);
                int frameNumber = toInt(buffer, 4);

                byte[] firstHalf = new byte[32];
                byte[] secondHalf = new byte[32];
                System.arraycopy(buffer, 8, firstHalf, 0, 32);       // buffer[8~39]
                System.arraycopy(buffer, 40, secondHalf, 0, 32);     // buffer[40~71]

                // 构造文本
                String message_1 = String.format("%-12s", " t: " + timestamp);
                String message_2 = " pkg: " + frameNumber;
                byte[] text_1 = message_1.getBytes(StandardCharsets.UTF_8);
                byte[] text_2 = message_2.getBytes(StandardCharsets.UTF_8);

                ByteArrayOutputStream body = new ByteArrayOutputStream();

                // 添加前32字节点阵
                for (int i = 0; i < 4; i++) {
                    body.write(0x01);
                    body.write(firstHalf, i * 8, 8);
                }

                for (byte b : text_1) {
                    body.write(0x00);
                    body.write(b);
                }

                for (int i = 0; i < 4; i++) {
                    body.write(0x01);
                    body.write(secondHalf, i * 8, 8);
                }

                for (byte b : text_2) {
                    body.write(0x00);
                    body.write(b);
                }

                byte[] payload = body.toByteArray();

                // 发送头
                out.write(0xAA);
                out.write(0x55);

                // 帧率（单位：毫秒）
                int frameIntervalMs = 33; // 可以改为动态或插入特殊测试值

                // 长度字段 = 帧率字段(2字节) + 数据体长度
                int totalBodyLength = 5 + payload.length;
                out.write(totalBodyLength); // 长度字段

                // 写入帧率字段（高字节在前）
                out.write((frameIntervalMs >> 8) & 0xFF);
                out.write(frameIntervalMs & 0xFF);

                // 写入主体数据
                out.write(payload);
                out.flush();

                System.out.println("已发送第 " + frameNumber + " 帧，时间戳：" + timestamp + "，间隔: " + frameIntervalMs + "ms");

                frameCount++;

                Thread.sleep(33); // 控制发送节奏
            }

            System.out.println("总共发送帧数: " + frameCount);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static int toInt(byte[] b, int offset) {
        return ((b[offset] & 0xFF) << 24) |
               ((b[offset + 1] & 0xFF) << 16) |
               ((b[offset + 2] & 0xFF) << 8) |
               (b[offset + 3] & 0xFF);
    }
}
