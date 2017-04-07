package android_serialport_api;

public class CRC16 {
	
	public static byte[] getBytes(String abc){
        String tmp="";
        byte[] bytes=new byte[abc.length()/2];
        for(int i=0;i<abc.length()-1;i++){
            if(i%2==0){
                tmp=abc.substring(i,i+2);
                bytes[i/2]=(byte)Integer.parseInt(tmp, 16);
            }
        }
        return bytes;

    }
	public static String getCRC(String str){
        String back="";
        int crc = 0xFFFF;          // initial value
        int polynomial = 0x1021;   // 0001 0000 0010 0001  (0, 5, 12)
        String tmp="";
        byte[] bytes=new byte[str.length()/2];
        for(int i=0;i<str.length()-1;i++){
            if(i%2==0){
                tmp=str.substring(i,i+2);
                bytes[i/2]=(byte)Integer.parseInt(tmp, 16);
            }
        }
        for (byte b : bytes) {
            for (int i = 0; i < 8; i++) {
                boolean bit = ((b   >> (7-i) & 1) == 1);
                boolean c15 = ((crc >> 15    & 1) == 1);
                crc <<= 1;
                if (c15 ^ bit) crc ^= polynomial;
             }
        }

        crc &= 0xffff;
        back=Integer.toHexString(crc);
        if(back.length() == 0){
            back = "0000";
        }else if(back.length() == 1){
            back = "000" + back;
        }else if(back.length() == 2){
            back = "00" + back;
        }else if(back.length() == 3){
            back = "0" + back;
        }

        return back;
	}

}
