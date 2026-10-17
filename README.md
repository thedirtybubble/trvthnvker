# trvthnvker
qol java agent that dewokes your base game and mods, dewokes the following
- translation keys (english only)
- splash text
- anything that relies on pridelib

and optionally removes
- telemetry
- realms

pass as a parameter
<br>
`-javaagent:trvthnvker.jar=strip-chat-keys,strip-realms`
<br><br>
OR as a property
<br>
`-Dstrip-chat-keys -Dstrip-realms=true`

tested on versions 1.6.1 to 26.2