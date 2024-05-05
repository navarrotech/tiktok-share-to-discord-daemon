# 3.0 Common Operational Picture "FlagShip"
Project 3.0 Common Operational Picture (COP) GUI.

Written in Kotlin, managed with Gradle, using Maven dependencies.

### Functionality:
- UI is written with [Preact.js](https://preactjs.com/guide/v10/differences-to-react)
- GUI Logic is written in [Kotlin](https://kotlinlang.org/)
- GUI window is hosted with [Java Swing](https://www.geeksforgeeks.org/introduction-to-java-swing/)
- Swing Chromium browser is powered by [JxBrowser](https://www.teamdev.com/jxbrowser)
- Data is transferred to/from the backend using [RTI DDS](https://www.rti.com/products/dds-standard)
- Map GUI uses [NASA WorldWind](https://worldwind.arc.nasa.gov/java/) (This might change in the future)

### Architecture
This code models applications like [Electron](https://www.electronjs.org/) or [Tauri](https://tauri.app/), but use Kotlin as the base language instead of Node or Rust. This will allow us to still leverage the power of HTML + CSS and hot reloading while leveraging high performance Kotlin code and native Java DDS.

The Kotlin application runs a swing GUI container, that runs a chromium web browser through the JxBrowser package.

The concept is that the frontend will auto connect to the DDS network with auto discovery. The user shouldn't need to enter any ip addresses or see any information for viewing what COP is connected to.

All logic, commands, and state should exist within Kotlin. Then we can use that Kotlin logic/code to be redistributed to other COPs like ATAK plugins and more. It's important that we decouple the UI from any logic, and keep the UI as flat and modular as possible. By decoupling UI and making logic re-usable, we can create a fantastic frontend application.

### Installation
You will need to install the following requirements to your host computer. Each website below should provide installation instructions which typically guide you through adding libraries, executables, etc to the system path or specific environment variables. 

Instructions:
1. Download & install the JDK 21 (Java compiler) https://www.oracle.com/java/technologies/downloads/#java21
2. In a terminal window, navigate to the cloned repository
3. Follow the environment .env instructions below to set your environment up. The project will not run without the .env file set.
4. You'll need to start the React.js UI. Change directories into "webui" and run "yarn run install" and "yarn run dev"
5. In a second terminal, you'll need to start the swing application and Kotlin to show the web UI.
6. To build the project, run: "./gradlew build"
7. To run the project, run: "./gradlew run"
8. If using VSCode, you can use tasks with ctrl + shift + b and choose "Run Flagship" to run steps 4 through 7 for you. It will automatically open the terminals and run the commands in your IDE.

### Environment .env
You'll need to create a .env file under the app directory for your services to start. .env should never be checked into git, and always under .gitignore.

Your environment file will need the following information:
```
# To identify dev vs prod, use keys 'development' or 'production' here:
ENV=development

# The following key is required for JxBrowser to work. https://teamdev.com/jxbrowser/docs/guides/introduction/licensing.html. Sign up for a free trial or wait for the company to get you a key.
JXBROWSER_LICENSE_KEY=xyz
```

### Production building
To build the project for production, run:
```
./gradlew production
``` 
to build the fatjar and the static web assets. Once you've got the production files built, run:
```
./gradlew windowInstaller (must be ran on Windows 11, need to install Wix 3.14)
./gradlew redhatInstaller (must be ran on Redhat, need to install RPM)
./gradlew debianInstaller (Can be ran on Debian or Redhat)
```
Once built, a .msi/.rpm/.deb will be created in the app directory.

To cleanup the production build files on your machine, run:
```
./gradlew productionClean
```
To super clean your dev environment, to the point where it's like you never built the project, run:
```
./gradlew realclean
```
