kill @e[type=block_display,tag=timber.d]
kill @e[type=marker,tag=timber.ctl]
kill @e[type=marker,tag=timber.m]
kill @e[type=marker,tag=timber.job]
kill @e[type=marker,tag=timber.cur]
kill @e[type=marker,tag=timber.pt]
kill @e[type=marker,tag=timber.mid]
function timber:uninstall/types
function timber:compat/extra_uninstall
function timber:uninstall/objectives
advancement revoke @a only timber:placed_log
tag @a remove timber.welcomed
tag @a remove timber.finder
tag @a remove timber.cutter
tag @a remove timber.owner
data remove storage timber:op t
data remove storage timber:op k
data remove storage timber:op recs
data remove storage timber:op drops
data remove storage timber:op f
data remove storage timber:op s
data remove storage timber:op cd
data remove storage timber:op seen
data remove storage timber:op slot
data remove storage timber:op tool
data remove storage timber:op m
data remove storage timber:op d
data remove storage timber:op it
data remove storage timber:op logs
data remove storage timber:anim s
data remove storage timber:anim c
data remove storage timber:anim it
data remove storage timber:curve fall
data remove storage timber:menu row
data remove storage timber:placed o
data remove storage timber:placed n
data remove storage timber:placed e
data remove storage timber:placed x
data remove storage timber:meta version
data remove storage timber:meta version_id
data remove storage timber:meta requires
tellraw @s ["",{text:"🪓 Timber data removed. ",color:"gold"},{text:"Now delete or disable the datapack (e.g. /datapack disable \"file/Timber-1.0.0.zip\") so it does not reinstall on the next /reload.",color:"gray"}]
