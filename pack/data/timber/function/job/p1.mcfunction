execute unless score #stage timber.data matches 0 run function timber:job/abort
execute as @e[type=marker,tag=timber.job,limit=1] at @s run function timber:job/start
