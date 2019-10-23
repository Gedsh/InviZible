package pan.alexander.tordnscrypt.utils.Installer;

import android.content.Context;

import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.ZipUtil.ZipFileManager;

public class DNSCryptInstallCommand extends AssetsExtractCommand {
    private PathVars pathVars;
    public DNSCryptInstallCommand(Context context, PathVars pathVars) {
        super(context);
        this.pathVars = pathVars;
    }

    @Override
    public void execute() throws Exception {
        ZipFileManager zipFileManager = new ZipFileManager();
        zipFileManager.extractZipFromInputStream(assets.open("DNSCrypt.zip"), pathVars.appDataDir);
    }
}
