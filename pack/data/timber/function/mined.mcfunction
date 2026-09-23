function timber:player/reset_stats
$data modify storage timber:op t set from storage timber:types $(t)
tag @s add timber.finder
function timber:find/start with storage timber:op t
tag @s remove timber.finder
