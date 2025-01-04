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

    Copyright 2019-2025 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.utils.parsers;

import static pan.alexander.tordnscrypt.di.SharedPreferencesModule.DEFAULT_PREFERENCES_NAME;
import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.Lazy;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.settings.dnscrypt_relays.DnsRelay;
import pan.alexander.tordnscrypt.settings.dnscrypt_relays.DnsServerRelay;
import pan.alexander.tordnscrypt.settings.dnscrypt_servers.DnsCryptResolver;
import pan.alexander.tordnscrypt.utils.filemanager.FileManager;

public class DnsCryptConfigurationParser {

    public Context context;
    public Lazy<SharedPreferences> defaultPreferences;
    public Lazy<PathVars> pathVars;

    @Inject
    public DnsCryptConfigurationParser(
            Context context,
            @Named(DEFAULT_PREFERENCES_NAME)
            Lazy<SharedPreferences> defaultPreferences,
            Lazy<PathVars> pathVars
    ) {
        this.context = context;
        this.defaultPreferences = defaultPreferences;
        this.pathVars = pathVars;
    }

    @WorkerThread
    public List<String> getDnsCryptProxyToml() {
        List<String> dnsCryptProxyToml = new ArrayList<>();
        try {
            List<String> lines = FileManager.readTextFileSynchronous(
                    context,
                    pathVars.get().getDnscryptConfPath()
            );
            for (String line : lines) {
                if (!line.isBlank()) {
                    dnsCryptProxyToml.add(line.trim());
                }
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
            }
        } catch (Exception e) {
            loge("DnsCryptServersParser getDnsCryptProxyToml", e);
        }
        return dnsCryptProxyToml;
    }

    public List<String> getDnsCryptServers(List<String> dnsCryptProxyToml) {
        List<String> servers = new ArrayList<>();
        try {
            for (String line : dnsCryptProxyToml) {
                if (line.matches("server_names .+")) {
                    String temp = line.substring(line.indexOf("[") + 1, line.indexOf("]")).trim();
                    temp = temp.replace("\"", "").replace("'", "").trim();
                    servers.addAll(Arrays.asList(temp.trim().split(", ?")));
                }
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
            }
        } catch (Exception e) {
            loge("DnsCryptServersParser getDnsCryptServers", e);
        }

        return servers;
    }

    public List<DnsServerRelay> getDnsCryptRoutes(List<String> dnsCryptProxyToml) {
        List<DnsServerRelay> routes = new ArrayList<>();
        try {
            boolean lockRoutes = false;
            for (String line : dnsCryptProxyToml) {
                if (line.startsWith("routes")) {
                    lockRoutes = true;
                } else if (lockRoutes && line.contains("server_name")) {
                    String serverName = "";
                    ArrayList<String> routesList = new ArrayList<>();

                    String[] rawStrArr = line.split(",");

                    for (String route : rawStrArr) {
                        route = route.replaceAll("via *= *", "")
                                .replaceAll("[^\\w\\-.=_]", "");

                        if (route.contains("server_name")) {
                            serverName = route.replaceAll("server_name *= *", "");
                        } else {
                            routesList.add(route);
                        }

                        if (Thread.currentThread().isInterrupted()) {
                            break;
                        }
                    }

                    if (!serverName.isEmpty() && !routesList.isEmpty()) {
                        routes.add(new DnsServerRelay(serverName, routesList));
                    }
                } else if (lockRoutes) {
                    lockRoutes = false;
                }
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
            }
        } catch (Exception e) {
            loge("DnsCryptServersParser getDnsCryptRoutes", e);
        }
        return routes;
    }

    @WorkerThread
    public List<String> getPublicResolversMd() {
        List<String> resolvers = new ArrayList<>();
        try {
            List<String> lines = FileManager.readTextFileSynchronous(
                    context,
                    pathVars.get().getDNSCryptPublicResolversPath()
            );
            for (String line : lines) {
                if (!line.isBlank()) {
                    resolvers.add(line);
                }
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
            }
        } catch (Exception e) {
            loge("DnsCryptServersParser getPublicResolversMd", e);
        }
        return resolvers;
    }

    @WorkerThread
    public List<String> getOwnResolversMd() {
        List<String> resolvers = new ArrayList<>();
        try {
            List<String> lines = FileManager.readTextFileSynchronous(
                    context,
                    pathVars.get().getDNSCryptOwnResolversPath()
            );
            for (String line : lines) {
                if (!line.isBlank()) {
                    resolvers.add(line.trim());
                }
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
            }
        } catch (Exception e) {
            loge("DnsCryptServersParser getOwnResolversMd", e);
        }
        return resolvers;
    }

    public Set<DnsCryptResolver> parseDnsCryptResolversMd(List<String> publicResolversMd) {
        Set<DnsCryptResolver> resolvers = new LinkedHashSet<>();
        try {
            StringBuilder sb = new StringBuilder();
            boolean lockServer = false;
            String name = "";
            for (String line : publicResolversMd) {
                if ((line.contains("##") || lockServer) && !line.isBlank()) {
                    if (line.startsWith("##")) {
                        lockServer = true;
                        name = line.substring(2).replaceAll("\\s+", "").trim();
                    } else if (line.startsWith("sdns")) {
                        String sdns = line.replace("sdns://", "").trim();
                        lockServer = false;
                        String description = sb.toString().replaceAll("\\s", " ");
                        sb.setLength(0);
                        if (!name.isEmpty()
                                && !description.isEmpty()
                                && !sdns.isEmpty()) {
                            resolvers.add(new DnsCryptResolver(name, description, sdns));
                        }
                        name = "";
                    } else if (lockServer) {
                        sb.append(line).append((char) 10);
                    }
                }
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
            }
        } catch (Exception e) {
            loge("DnsCryptServersParser parseDnsCryptResolversMd", e);
        }
        return resolvers;
    }

    @WorkerThread
    public List<String> getOdohServersMd() {
        List<String> servers = new ArrayList<>();
        try {
            List<String> lines = FileManager.readTextFileSynchronous(
                    context,
                    pathVars.get().getOdohServersPath()
            );
            for (String line : lines) {
                if (!line.isBlank()) {
                    servers.add(line);
                }
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
            }
        } catch (Exception e) {
            loge("DnsCryptServersParser getOdohServersMd", e);
        }
        return servers;
    }

    @WorkerThread
    public List<String> getDnsCryptRelaysMd() {
        List<String> servers = new ArrayList<>();
        try {
            List<String> lines = FileManager.readTextFileSynchronous(
                    context,
                    pathVars.get().getDNSCryptRelaysPath()
            );
            for (String line : lines) {
                if (!line.isBlank()) {
                    servers.add(line);
                }
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
            }
        } catch (Exception e) {
            loge("DnsCryptServersParser getDnsCryptRelaysMd", e);
        }
        return servers;
    }

    @WorkerThread
    public List<String> getOdohRelaysMd() {
        List<String> servers = new ArrayList<>();
        try {
            List<String> lines = FileManager.readTextFileSynchronous(
                    context,
                    pathVars.get().getOdohRelaysPath()
            );
            for (String line : lines) {
                if (!line.isBlank()) {
                    servers.add(line);
                }
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
            }
        } catch (Exception e) {
            loge("DnsCryptServersParser getOdohRelaysMd", e);
        }
        return servers;
    }

    public Set<DnsRelay> parseDnsCryptRelaysMd(List<String> relaysMd) {
        Set<DnsRelay> relays = new LinkedHashSet<>();
        try {
            String name = "";
            String description = "";
            boolean lockRelay = false;

            for (String line : relaysMd) {

                if (line.startsWith("##")) {
                    name = line.replace("##", "").trim();
                    lockRelay = true;
                } else if (lockRelay && line.startsWith("sdns://")) {
                    lockRelay = false;
                } else if (lockRelay) {
                    description = line.replaceAll("\\s", " ").trim();
                }

                if (!name.isEmpty() && !description.isEmpty() && !lockRelay) {
                    DnsRelay dnsRelayItem = new DnsRelay(name, description);
                    relays.add(dnsRelayItem);
                    name = "";
                    description = "";
                }
            }
        } catch (Exception e) {
            loge("DnsCryptServersParser parseDnsCryptRelaysMd", e);
        }
        return relays;
    }

    @WorkerThread
    public void saveOwnResolversMd(List<String> lines) {
        FileManager.writeToTextFile(
                context,
                pathVars.get().getDNSCryptOwnResolversPath(),
                lines,
                "ignored"
        );
    }

    @WorkerThread
    public void saveDnsCryptProxyToml(List<String> lines) {
        FileManager.writeToTextFile(
                context,
                pathVars.get().getDnscryptConfPath(),
                lines,
                "ignored"
        );
    }
}
