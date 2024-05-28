using System;
using System.Collections.Generic;
using System.Linq;
using System.Net.Sockets;
using System.Text;
using System.Threading.Tasks;

namespace SmartConsole
{
    class Program
    {
        static void Main(string[] args)
        {
            /*
            {
                ConsoleKeyInfo cki = Console.ReadKey();
                String ss = Enum.GetName(cki.Key.GetType(), cki.Key);
                Console.WriteLine(ss);
                int abc = Console.Read();
                Console.WriteLine(abc);
                Console.ReadKey();
                return;
            }
            */
            //Console.OpenStandardOutput().Write(new byte[] { (byte)'H'}, 0, 1);
            int port = Convert.ToInt32(args[0]);
            Socket s = new Socket(AddressFamily.InterNetwork, SocketType.Stream, ProtocolType.Tcp);
            s.Connect("localhost", port);
            DataInputStream dis = new DataInputStream(s);
            while (true) {
                int act = dis.readByte();
                if (act == 'X') {
                    s.Close();
                    return;
                } else if (act == 'R') {
                    ReadKey(s);
                } else if (act == 'W') {
                    int len = dis.readInt();
                    byte[] data = dis.readFully(len);
                    char[] a = new char[data.Length];
                    for (int i = 0; i < a.Length; i++) a[i] = (char)(data[i] & 0xFF);
                    Console.Write(new String(a));
                } else if (act == 'C') {
                    try{
                        int target = dis.readByte();
                        int len = dis.readByte();
                        byte[] data = dis.readFully(len);
                        char[] a = new char[data.Length];
                        for (int i = 0; i < a.Length; i++) a[i] = (char)(data[i] & 0xFF);
                        ConsoleColor c = (ConsoleColor)Enum.Parse(ConsoleColor.Black.GetType(), new String(a));
                        if (target == 'F') { //else 'B'
                            Console.ForegroundColor = c;
                        } else {
                            Console.BackgroundColor = c;
                        }
                    }
                    catch(Exception e){
                        //Console.WriteLine(e);
                    }
                } else if (act == 'N') {
                    Console.Clear();
                } else if (act == 'T') {
                    s.Send(new byte[] { (byte)(Console.KeyAvailable ? 1 : 0) }, 1, SocketFlags.None);
                }
            }
        }

        static void ReadKey(Socket s)
        {
            ConsoleKeyInfo cki = Console.ReadKey();
            //s.Send(new byte[] {(byte)cki.KeyChar}, 1, SocketFlags.None);
            char[] arr = Enum.GetName(cki.Key.GetType(), cki.Key).ToCharArray();
            byte[] arr2 = new byte[arr.Length];
            for (int i = 0; i < arr.Length; i++) arr2[i] = (byte)arr[i];
            s.Send(new byte[] { (byte)cki.KeyChar }, 1, SocketFlags.None);
            s.Send(new byte[] { (byte)arr2.Length }, 1, SocketFlags.None);
            s.Send(arr2, 0, arr2.Length, SocketFlags.None);
        }
    }

    public class DataInputStream
    {
        Socket socket;
        public DataInputStream(Socket s)
        {
            this.socket = s;
        }

        public byte[] readFully(int l)
        {
            byte[] output = new byte[l];
            byte[] temp = new byte[1024];

            int p = 0;
            while (p < l)
            {
                p += socket.Receive(output, p, l - p, SocketFlags.None);
            }
            return output;
        }

        public int readByte()
        {
            byte[] raw = readFully(1);
            byte[] temp = new byte[] { raw[0], 0, 0, 0 };
            return System.BitConverter.ToInt32(temp, 0);
        }

        public int readShort()
        {
            byte[] raw = readFully(2);
            byte[] temp = new byte[] { raw[1], raw[0], 0, 0 };
            return System.BitConverter.ToInt32(temp, 0);
        }

        public int readInt()
        {
            byte[] raw = readFully(4);
            byte[] temp = new byte[] { raw[3], raw[2], raw[1], raw[0] };
            return System.BitConverter.ToInt32(temp, 0);
        }

        public float readFloat()
        {
            byte[] raw = readFully(4);
            byte[] temp = new byte[] { raw[3], raw[2], raw[1], raw[0] };
            return System.BitConverter.ToSingle(temp, 0);
        }

        public double readDouble()
        {
            byte[] raw = readFully(8);
            byte[] temp = new byte[] { raw[7], raw[6], raw[5], raw[4], raw[3], raw[2], raw[1], raw[0] };
            return System.BitConverter.ToDouble(temp, 0);
        }
    }

}
