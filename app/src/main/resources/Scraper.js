function sleep(time = 1_000) {
    return new Promise(resolve => setTimeout(resolve, time))
}

function waitForElementToExist(queryString, loopTime = 100){
    console.log(" >> Waiting for element to exist: ", queryString)
    return new Promise(resolve => {
        const interval = setInterval(() => {
            const element = document.querySelector(queryString)
            if (element) {
                clearInterval(interval)
                console.log(" >> Element found")
                resolve(element)
            }
        }, loopTime)
    })
}

async function gatherMessageDataFromPage() {
    return {
        url: window.location.href,
        video: document.querySelector("video").src,
        author: document.querySelector("[data-e2e=browse-username]").innerText,
        description: document.querySelector("[data-e2e=browse-video-desc]").innerText,
    }
}

function getConversations() {
    const conversations = document.querySelectorAll("[data-e2e=chat-list-item]")

    const values = []
    conversations.forEach(conversation => {
        const texts = conversation.innerText.split("\n\n")
        const remapped = {
            username: texts[0],
            lastMessage: texts[1],
            time: texts[2],
            getMessages: async () => {
                conversation.click()
                await waitForElementToExist("[data-e2e=chat-item]")
                const messages = document.querySelectorAll("[data-e2e=chat-item]")

                const messageArray = []

                let isFirst = true;
                for (const message of [...messages]) {
                    let hasVideo = false
                    let childWithVideoStuff = null
                    message.children[0].children.forEach(child => {
                        if (child.className.includes("DivVideoContainer")){
                            childWithVideoStuff = child
                            hasVideo = true
                        }
                    })
                    if (hasVideo) {
                        await sleep(1_000)
                        if (isFirst) {
                            childWithVideoStuff.click()
                            isFirst = false
                        }
                        childWithVideoStuff.children[0].click()
                        await waitForElementToExist("[data-e2e=browse-video-desc]")
                        messageArray.push(
                            await gatherMessageDataFromPage()
                        )

                        document.querySelector("[data-e2e=browse-close]").click()
                        await sleep(500)
                    }
                }

                return messageArray;
            }
        }

        values.push(remapped)
    })

    return values;
}

async function doTheThing(){
    if (isRunning) {
        return;
    }

    isRunning = true;
    const conversations = getConversations()

    for (const conversation of conversations) {
        const messages = await conversation.getMessages()
        for (const message of messages) {
            window.reportConversation(conversation.username, ...Object.values(message))
        }
    }

    isRunning = false;
}

let isRunning = false;
setInterval(doTheThing, 10_000)
waitForElementToExist("[data-e2e=chat-list-item]")
    .then(() => doTheThing())
