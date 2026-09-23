execute if score @s timber matches 1 store success score @s timber.off unless score @s timber.off matches 1
execute if score @s timber matches 2 run scoreboard players set @s timber.off 0
execute if score @s timber matches 3 run scoreboard players set @s timber.off 1
scoreboard players set @s timber 0
execute if score @s timber.off matches 1 run return run tellraw @s ["",{text:"🪓 Timber ",color:"gold"},{text:"disabled",color:"red"},{text:" for you. ",color:"gray"},{text:"[Turn on]",color:"green",hover_event:{action:"show_text",value:"/trigger timber"},click_event:{action:"run_command",command:"/trigger timber"}}]
tellraw @s ["",{text:"🪓 Timber ",color:"gold"},{text:"enabled",color:"green"},{text:" for you. ",color:"gray"},{text:"[Turn off]",color:"red",hover_event:{action:"show_text",value:"/trigger timber"},click_event:{action:"run_command",command:"/trigger timber"}}]
