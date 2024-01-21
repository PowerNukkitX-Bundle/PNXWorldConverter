### Usage

```cmd
java -jar pnxworldconvert.jar -t save_path -d dimension_name
```

> [!CAUTION]
> Warning: often when you download **pnxworldconvert** the version is written behind, so don't forget to either rename the file to `pnxworldconvert` or modify the command line by just adding the version of **pnxworldconvert** you downloaded.
> Exemple:
> ```cmd
> java -jar pnxworldconvert-1.0.9.jar -t save_path -d dimension_name
> ```


### Params

**save_path example:**

```
D:\your device path\1.19.2\.MINECRAFT\SAVES\new world  ←this is save_path
├─advancements
├─data
├─datapacks
├─DIM-1
│  ├─data
│  ├─entities
│  └─region
├─DIM1
│  └─data
├─entities
├─playerdata
├─poi
├─region
└─stats
```

**Dimension_name valid values: `OVERWORLD`  `NETHER` `END`**

### Support Version

|   Version   | Available |
|:-----------:|:---------:|
|  Below 1.7  |     ❓     |
|  1.7-1.12   |     ✅     |
|  1.13-1.14  |     ❌     |
| 1.15-1.20.1 |     ✅     |

### Tips

If your map is downloaded from the Internet, to avoid problems due to the mca export format  
Please run the level through the corresponding version of the minecraft client before convert.
