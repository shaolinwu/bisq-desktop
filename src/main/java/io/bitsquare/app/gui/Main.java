/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.app.gui;

import io.bitsquare.app.ArgumentParser;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.SystemTray;
import io.bitsquare.gui.ViewLoader;
import io.bitsquare.gui.components.Popups;
import io.bitsquare.gui.util.ImageUtil;
import io.bitsquare.persistence.Persistence;
import io.bitsquare.settings.Settings;
import io.bitsquare.user.User;
import io.bitsquare.util.ConfigLoader;

import com.google.common.base.Throwables;

import com.google.inject.Guice;
import com.google.inject.Injector;

import java.io.IOException;

import java.util.Properties;

import javafx.application.Application;
import javafx.scene.*;
import javafx.scene.image.*;
import javafx.scene.input.*;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lighthouse.files.AppDirectory;
import net.sourceforge.argparse4j.inf.Namespace;

import static io.bitsquare.app.AppModule.APP_NAME_KEY;
import static io.bitsquare.msg.tomp2p.TomP2PMessageModule.*;

public class Main extends Application {
    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static String appName = "Bitsquare";
    private static Properties properties;

    private MainModule mainModule;
    private Injector injector;

    public static void main(String[] args) {
        Namespace argumentsNamespace = new ArgumentParser().parseArgs(args);

        if (argumentsNamespace.getString(APP_NAME_KEY) != null)
            appName = appName + "-" + argumentsNamespace.getString(APP_NAME_KEY);

        properties = ConfigLoader.loadConfig(appName);

        properties.setProperty(APP_NAME_KEY, appName);

        if (argumentsNamespace.getString(BOOTSTRAP_NODE_ID_KEY) != null)
            properties.setProperty(BOOTSTRAP_NODE_ID_KEY, argumentsNamespace.getString(BOOTSTRAP_NODE_ID_KEY));

        if (argumentsNamespace.getString(BOOTSTRAP_NODE_IP_KEY) != null)
            properties.setProperty(BOOTSTRAP_NODE_IP_KEY, argumentsNamespace.getString(BOOTSTRAP_NODE_IP_KEY));

        if (argumentsNamespace.getString(BOOTSTRAP_NODE_PORT_KEY) != null)
            properties.setProperty(BOOTSTRAP_NODE_PORT_KEY, argumentsNamespace.getString(BOOTSTRAP_NODE_PORT_KEY));

        if (argumentsNamespace.getString(NETWORK_INTERFACE_KEY) != null)
            properties.setProperty(NETWORK_INTERFACE_KEY, argumentsNamespace.getString(NETWORK_INTERFACE_KEY));

        Application.launch(Main.class, args);
    }

    @Override
    public void start(Stage primaryStage) {
        mainModule = new MainModule(properties, primaryStage);
        injector = Guice.createInjector(mainModule);


        // route uncaught exceptions to a user-facing dialog

        Thread.currentThread().setUncaughtExceptionHandler((thread, throwable) ->
                Popups.handleUncaughtExceptions(Throwables.getRootCause(throwable)));


        // configure the Bitsquare application data directory

        try {
            AppDirectory.initAppDir(appName);
        } catch (IOException e) {
            log.error(e.getMessage());
        }


        // load and apply any stored settings

        User user = injector.getInstance(User.class);
        Settings settings = injector.getInstance(Settings.class);
        Persistence persistence = injector.getInstance(Persistence.class);
        persistence.init();

        User persistedUser = (User) persistence.read(user);
        user.applyPersistedUser(persistedUser);

        settings.applyPersistedSettings((Settings) persistence.read(settings.getClass().getName()));


        // load the main view and create the main scene

        ViewLoader.setInjector(injector);
        ViewLoader loader = new ViewLoader(Navigation.Item.MAIN, false);
        Parent view = loader.load();

        Scene scene = new Scene(view, 1000, 600);
        scene.getStylesheets().setAll(
                "/io/bitsquare/gui/bitsquare.css",
                "/io/bitsquare/gui/images.css");


        // configure the system tray

        SystemTray systemTray = new SystemTray(primaryStage, this::stop);
        primaryStage.setOnCloseRequest(e -> systemTray.hideStage());
        scene.setOnKeyReleased(keyEvent -> {
            if (new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN).match(keyEvent))
                systemTray.hideStage();
        });


        // configure the primary stage

        primaryStage.setTitle(appName);
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(75);
        primaryStage.setMinHeight(50);
        primaryStage.getIcons().add(new Image(getClass().getResourceAsStream(
                ImageUtil.isRetina() ? "/images/window_icon@2x.png" : "/images/window_icon.png")));


        // make the UI visible

        primaryStage.show();
    }

    @Override
    public void stop() {
        mainModule.close(injector);
        System.exit(0);
    }
}
