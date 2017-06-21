package io.xol.chunkstories.net.packets;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import io.xol.chunkstories.api.exceptions.PacketProcessingException;
import io.xol.chunkstories.api.net.Packet;
import io.xol.chunkstories.api.net.PacketDestinator;
import io.xol.chunkstories.api.net.PacketSender;
import io.xol.chunkstories.api.net.PacketsProcessor;

//(c) 2015-2017 XolioWare Interactive
//http://chunkstories.xyz
//http://xol.io

public class PacketSendFile extends Packet
{
	public String fileTag;
	public File file;

	@Override
	public void send(PacketDestinator destinator, DataOutputStream out) throws IOException
	{
		out.writeUTF(fileTag);

		if (file.exists())
		{
			out.writeLong(file.length());
			FileInputStream fis = new FileInputStream(file);
			byte[] buffer = new byte[4096];
			int read;
			while(true)
			{
				read = fis.read(buffer);
				//System.out.println("read"+read);
				if(read > 0)
					out.write(buffer, 0, read);
				else
					break;
			}
			fis.close();
		}
		else
			out.writeLong(0L);
	}

	@Override
	public void process(PacketSender sender, DataInputStream in, PacketsProcessor processor) throws IOException, PacketProcessingException
	{
		//Ignore packets incomming
	}

}