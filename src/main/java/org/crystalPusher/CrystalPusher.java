package org.crystalPusher;

import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.plugin.Plugin;

public class CrystalPusher extends Plugin {
	@Override
	public void onLoad() {
		final CrystalPusherModule crystalPusherModule = new CrystalPusherModule();
		RusherHackAPI.getModuleManager().registerFeature(crystalPusherModule);
	}

	@Override
	public void onUnload() {

	}
}