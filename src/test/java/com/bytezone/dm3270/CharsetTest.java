package com.bytezone.dm3270;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class CharsetTest {

	@Test
	public void charsetTest() {
		assertArrayEquals(new byte[]{0x4,0x17},Charset.CP1047.getCode());
		assertArrayEquals(new byte[]{0x4,0x7B},Charset.CP1147.getCode());
		assertArrayEquals(new byte[]{0x0,0x25},Charset.CP037.getCode());
		assertArrayEquals(new byte[]{(byte)0x83,(byte)0xBA},Charset.CP33722.getCode());
	}

	@Test
	public void loadingAllCharset(){
		for (Charset charset : Charset.values()) {
			charset.load();
		}
	}
}