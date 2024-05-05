# Tiktok Reposting To Discord Bot

### Installation
You will need to install the following requirements to your host computer. Each website below should provide installation instructions which typically guide you through adding libraries, executables, etc to the system path or specific environment variables. 

Instructions:
1. Download & install the JDK 21 (Java compiler) https://www.oracle.com/java/technologies/downloads/#java21
2. In a terminal window, navigate to the cloned repository
3. To run the project, run: "./gradlew build un"

### Environment .env
You'll need to create a .env file under the app directory for your services to start. .env should never be checked into git, and always under .gitignore.

Your environment file will need the following information:
```
# To identify dev vs prod, use keys 'development' or 'production' here:
ENV=development

# Versioning (to be implemented)
APP_VERSION=dev

# The following key is required for JxBrowser to work. https://teamdev.com/jxbrowser/docs/guides/introduction/licensing.html. Sign up for a free trial or wait for the company to get you a key.
JXBROWSER_LICENSE_KEY=xyz

# Tiktok credentails of the bot that's listening to incoming messages
TIKTOK_USERNAME=username # NOT EMAIL, NOT PHONE NUMBER
TIKTOK_PASSWORD=password

# Database
DATABASE_URL=jdbc:postgresql://localhost:5432/postgres
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=postgres
```
