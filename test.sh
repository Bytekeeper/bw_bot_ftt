#!/usr/bin/env bash
docker rm -vf $(docker ps -a -f name=GAME -q)
cp build/libs/FFT_TER-all-1.0-SNAPSHOT.jar ~/.scbw/bots/StyxZ/AI/
mapfile -t maps < <(find ~/.scbw/maps/sscai -name '*.sc?' -printf "sscai/%P\n")

function play() {
  (
    trap "" HUP
    echo 'START'
    game_name=${1// /_}
    rm -rf ~/.scbw/games/GAME_$game_name
    map=${maps[$RANDOM % ${#maps[@]} ]}
    echo "Playing vs $1 on $map"
    scbw.play --headless --read_overwrite --bots StyxZ "$1" --timeout_at_frame 28000 --game_name $game_name --map "$map"
    cp ~/.scbw/games/GAME_$game_name/write_0/trace.json ~/cherryvis-docker/replays/$game_name.rep.cvis
    cp ~/.scbw/games/GAME_$game_name/player_0.rep ~/cherryvis-docker/replays/$game_name.rep
    echo 'DONE'
  ) &
}
#nohup scbw.play --headless --bots StyxZ "Bryan Weber" --timeout_at_frame 43200 --game_name shark&
#nohup scbw.play --headless --bots StyxZ "McRaveZ" --timeout_at_frame 43200 --game_name shark&
# nohup scbw.play --headless --bots StyxZ MadMixT --timeout_at_frame 43200 --game_name shark&
#nohup scbw.play --headless --bots StyxZ "PurpleSwarm" --timeout_at_frame 43200 --game_name shark&
# nohup scbw.play --headless --bots StyxZ "lol" --timeout_at_frame 43200 --game_name shark&
# nohup scbw.play --headless --bots StyxZ "Simon Prins" --timeout_at_frame 43200 --game_name shark&
#nohup scbw.play --headless --bots StyxZ Locutus --timeout_at_frame 43200 --game_name shark&
# nohup scbw.play --headless --bots StyxZ Antiga --timeout_at_frame 43200 --game_name shark&
#nohup scbw.play --headless --bots StyxZ 'Chris Coxe' --timeout_at_frame 43200 --game_name shark&
#nohup scbw.play --headless --bots StyxZ "Stone" --timeout_at_frame 43200 --game_name shark&
 #nohup scbw.play --headless --bots StyxZ tscmoo --timeout_at_frame 43200 --game_name shark&
# nohup scbw.play --headless --bots StyxZ WuliBot --timeout_at_frame 43200 --game_name shark&
#nohup scbw.play --headless --bots StyxZ "Andrew Smith" --timeout_at_frame 43200 --game_name shark&
#play CUBOT
#play Stone
#play McRaveZ
#play 'Chris Coxe'
# play Antiga
play Locutus
#play Proxy
play BananaBrain
# play tscmoo
# play WuliBot
#play 'Iron bot'
play TyrProtoss
#play 'Tomas Vajda'
play McRave
#play ZurZurZur
# play Flash
#play 'Simon Prins'
#play 'Andrew Smith'
# play Bereaver
#play WillBot
# play Crona
# play Ecgberht
play Microwave
#play ZNZZBot
# play GuiBot
# play AntigaZ
#play Feint
#play PurpleSwarm
#play Steamhammer
sleep 5
