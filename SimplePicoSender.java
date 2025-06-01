import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class SimplePicoSender {
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

                // 直接从 buffer 分离前后 32 字节帧数据
            byte[] firstHalf = new byte[32];
            byte[] secondHalf = new byte[32];
            System.arraycopy(buffer, 8, firstHalf, 0, 32);       // buffer[8~39]
            System.arraycopy(buffer, 40, secondHalf, 0, 32);     // buffer[40~71]



                byte endFlag = buffer[72]; // 应该是0xFF

                // 构造文本信息
                // String message_1 = "pkg:" + frameNumber;
                // String message_2 = " T:" + timestamp;
                String message_1 = " t: " + timestamp;
                message_1 = String.format("%-12s", message_1);
                String message_2 = " pkg: " + frameNumber;
                byte[] text_1 = message_1.getBytes(StandardCharsets.UTF_8);
                byte[] text_2 = message_2.getBytes(StandardCharsets.UTF_8);

                ByteArrayOutputStream body = new ByteArrayOutputStream();

                // 点阵数据前每8字节加 0x01
                for (int i = 0; i < 4; i++) {
                    body.write(0x01);
                    body.write(firstHalf, i * 8, 8);
                }

                // UTF-8文本，每字节前加0x00
                for (byte b : text_1) {
                    body.write(0x00);
                    body.write(b);
                }

                // 点阵数据前每8字节加 0x01
                for (int i = 0; i < 4; i++) {
                    body.write(0x01);
                    body.write(secondHalf, i * 8, 8);
                }

                // UTF-8文本，每字节前加0x00
                for (byte b : text_2) {
                    body.write(0x00);
                    body.write(b);
                }

                byte[] data = body.toByteArray();

                // 发送完整包头 + 长度 + 数据体
                out.write(0xAA);
                out.write(0x55);
                out.write(data.length);
                out.flush();

                out.write(data);
                out.flush();

                System.out.println("已发送第 " + frameNumber + " 个包，时间戳：" + timestamp);

                frameCount++;

                //Thread.sleep(66);
                Thread.sleep(33);
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
