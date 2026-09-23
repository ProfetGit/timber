execute if block ~1 ~ ~ #minecraft:leaves[persistent=false,distance=2] positioned ~1 ~ ~ run function timber:leaves/s/px2
execute if block ~-1 ~ ~ #minecraft:leaves[persistent=false,distance=2] positioned ~-1 ~ ~ run function timber:leaves/s/nx2
execute if block ~ ~1 ~ #minecraft:leaves[persistent=false,distance=2] positioned ~ ~1 ~ run function timber:leaves/s/py2
execute if block ~ ~-1 ~ #minecraft:leaves[persistent=false,distance=2] positioned ~ ~-1 ~ run function timber:leaves/s/ny2
execute if block ~ ~ ~1 #minecraft:leaves[persistent=false,distance=2] positioned ~ ~ ~1 run function timber:leaves/s/pz2
execute if block ~ ~ ~-1 #minecraft:leaves[persistent=false,distance=2] positioned ~ ~ ~-1 run function timber:leaves/s/nz2
