Array.prototype.getRandom = function() {
    return this[Math.floor(Math.random() * this.length)];
};
