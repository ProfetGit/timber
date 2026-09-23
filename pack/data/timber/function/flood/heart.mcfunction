execute if entity @e[type=marker,tag=timber.m,distance=..0.1] run return 0
execute summon marker run function timber:flood/heart_init
function timber:flood/neighbors with storage timber:op t
