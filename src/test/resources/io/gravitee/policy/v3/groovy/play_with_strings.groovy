
def prefix = "prefix";
def separator = "_";

def getContent(prefix, separator, suffix) {
    def content = "${prefix}${separator}${suffix}"
    return content.trim();
}

return getContent(prefix, separator, 'suffix')
