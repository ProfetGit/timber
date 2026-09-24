execute unless data storage timber:meta version run return 0
scoreboard players enable @a timber
execute as @a[scores={timber=1..}] run function timber:player/toggle
execute as @a[tag=!timber.welcomed] run function timber:player/welcome
function timber:tick/mined
function timber:compat/extra_tick
