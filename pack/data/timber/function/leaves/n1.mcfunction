execute if block ~1 ~ ~ #minecraft:leaves[persistent=false,distance=1] positioned ~1 ~ ~ run function timber:leaves/s/px1
execute if block ~-1 ~ ~ #minecraft:leaves[persistent=false,distance=1] positioned ~-1 ~ ~ run function timber:leaves/s/nx1
execute if block ~ ~1 ~ #minecraft:leaves[persistent=false,distance=1] positioned ~ ~1 ~ run function timber:leaves/s/py1
execute if block ~ ~-1 ~ #minecraft:leaves[persistent=false,distance=1] positioned ~ ~-1 ~ run function timber:leaves/s/ny1
execute if block ~ ~ ~1 #minecraft:leaves[persistent=false,distance=1] positioned ~ ~ ~1 run function timber:leaves/s/pz1
execute if block ~ ~ ~-1 #minecraft:leaves[persistent=false,distance=1] positioned ~ ~ ~-1 run function timber:leaves/s/nz1
