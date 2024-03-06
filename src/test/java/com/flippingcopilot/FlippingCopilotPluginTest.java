package com.flippingcopilot;

import com.flippingcopilot.controller.FlippingCopilotPlugin;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class FlippingCopilotPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(FlippingCopilotPlugin.class);
		RuneLite.main(args);
	}
}