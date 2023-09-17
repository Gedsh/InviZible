/*
    This file is part of InviZible Pro.

    InviZible Pro is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    InviZible Pro is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with InviZible Pro.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2019-2023 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.installer;

import android.content.Context;

import pan.alexander.tordnscrypt.utils.zipUtil.ZipFileManager;

public class ITPDExtractCommand extends AssetsExtractCommand {
    private final String appDataDir;

    public ITPDExtractCommand(Context context, String appDataDir) {
        super(context);
        this.appDataDir = appDataDir;
    }

    @Override
    public void execute() throws Exception {
        ZipFileManager zipFileManager = new ZipFileManager();
        zipFileManager.extractZipFromInputStream(assets.open("itpd.mp3"), appDataDir);
    }
}
