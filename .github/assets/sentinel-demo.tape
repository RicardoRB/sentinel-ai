# Records the README demo. From the repository root, with `sentinel` on PATH:
#   vhs .github/assets/sentinel-demo.tape
#   gif2webp -min_size -lossy -q 80 .github/assets/sentinel-demo.gif -o .github/assets/sentinel-demo.webp
#   rm .github/assets/sentinel-demo.gif

Output .github/assets/sentinel-demo.gif
Output .github/assets/sentinel-demo.webm

Set Shell bash
Set FontSize 18
Set Width 1320
Set Height 720
Set Padding 24
Set Margin 24
Set MarginFill "#0b1220"
Set WindowBar Colorful
Set BorderRadius 12
Set Theme "Catppuccin Mocha"
Set TypingSpeed 45ms

Hide
Type "export GIT_PAGER=cat REPO=$PWD DEMO=$(mktemp -d)/orders" Enter
Type "sh .github/assets/sentinel-demo-setup.sh $DEMO && cd $DEMO" Enter
Wait@5m
Type "PS1='\[\e[1;34m\]orders\[\e[0m\] $ ' && clear" Enter
Sleep 500ms
Show

Type "# 1 · Edit: an agent just changed OrderService. Sentinel checks it." Enter
Sleep 800ms
Type "sentinel check" Enter
Wait+Screen@60s /Quality Gate: \w+/
Sleep 3.5s

Hide
Type "sed -i.bak 's/createdAt + 3600/createdAt + SECONDS_PER_HOUR/' src/main/java/com/acme/OrderService.java && rm src/main/java/com/acme/OrderService.java.bak && clear" Enter
Sleep 500ms
Show
Type "# The agent reads the finding and uses the constant instead." Enter
Sleep 600ms
Type "git diff --color" Enter
Sleep 3s

Type "sentinel check" Enter
Wait+Screen@60s /Quality Gate: \w+/
Sleep 2s

Type "clear" Enter
Type "# 3 · Push: stricter gates before the code leaves your machine." Enter
Sleep 800ms
Type "sentinel check --profile pre-push" Enter
Wait+Screen@120s /Quality Gate: \w+/
Sleep 4s
