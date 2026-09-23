scoreboard players add #calls_a tbtest 1
execute store success score #player tbtest if entity @s[type=player]
execute summon marker run function tbtest:where
execute if entity @s[tag=tbtest.veto_a] run return 1
