execute if score #logs timber.data >= #max_logs timber.config run return 0
execute summon marker unless function timber:flood/init run return 0
$execute if block ~ ~ ~ $(log)[axis=y] run setblock ~ ~ ~ $(wood)[axis=y] strict
$execute if block ~ ~ ~ $(log)[axis=x] run setblock ~ ~ ~ $(wood)[axis=x] strict
$execute if block ~ ~ ~ $(log)[axis=z] run setblock ~ ~ ~ $(wood)[axis=z] strict
scoreboard players add #logs timber.data 1
function timber:flood/neighbors with storage timber:op t
