const state = {
  preflightCount: 0,
  preflightHeaders: "",
  redirectHits: 0,
  slowStarted: 0,
  slowAborted: 0,
};

function resetState() {
  state.preflightCount = 0;
  state.preflightHeaders = "";
  state.redirectHits = 0;
  state.slowStarted = 0;
  state.slowAborted = 0;
}

function setCorsHeaders(req, res) {
  if (req.headers.origin) {
    res.setHeader("Access-Control-Allow-Origin", "*");
    res.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
    res.setHeader("Access-Control-Allow-Headers", "Content-Type, X-Notifly-SDK-Version");
    res.setHeader(
      "Access-Control-Expose-Headers",
      "X-Observed-Cookie, X-Observed-Accept-Charset, X-Observed-Preflight-Count, X-Observed-Preflight-Headers, X-Redirect-Hits, X-Slow-Started, X-Slow-Aborted",
    );
  }
}

function completeRequest(req, callback) {
  req.on("data", () => {});
  req.on("end", callback);
}

const fixtureFactory = () => (req, res, next) => {
  const path = new URL(req.url, "http://fixture.invalid").pathname;
  if (!path.startsWith("/__notifly_popup_fixture/")) {
    next();
    return;
  }

  setCorsHeaders(req, res);
  if (req.method === "OPTIONS") {
    state.preflightCount += 1;
    state.preflightHeaders = req.headers["access-control-request-headers"] || "";
    res.statusCode = 204;
    res.end();
    return;
  }

  if (path.endsWith("/reset")) {
    resetState();
    res.statusCode = 204;
    res.end();
    return;
  }

  if (path.endsWith("/state")) {
    res.setHeader("X-Observed-Preflight-Count", String(state.preflightCount));
    res.setHeader("X-Observed-Preflight-Headers", state.preflightHeaders || "absent");
    res.setHeader("X-Redirect-Hits", String(state.redirectHits));
    res.setHeader("X-Slow-Started", String(state.slowStarted));
    res.setHeader("X-Slow-Aborted", String(state.slowAborted));
    res.statusCode = 204;
    res.end();
    return;
  }

  if (path.endsWith("/html")) {
    completeRequest(req, () => {
      res.setHeader("Content-Type", "text/html; charset=utf-8");
      res.setHeader("X-Observed-Cookie", req.headers.cookie || "absent");
      res.setHeader("X-Observed-Accept-Charset", req.headers["accept-charset"] || "absent");
      res.setHeader("X-Observed-Preflight-Count", String(state.preflightCount));
      res.setHeader("X-Observed-Preflight-Headers", state.preflightHeaders || "absent");
      res.statusCode = 200;
      res.end("<div>popup fixture</div>");
    });
    return;
  }

  if (path.endsWith("/empty")) {
    completeRequest(req, () => {
      res.statusCode = 204;
      res.end();
    });
    return;
  }

  if (path.endsWith("/redirect")) {
    res.statusCode = 302;
    res.setHeader("Location", "/__notifly_popup_fixture/redirect-target");
    res.end();
    return;
  }

  if (path.endsWith("/redirect-target")) {
    state.redirectHits += 1;
    res.statusCode = 200;
    res.end("redirected");
    return;
  }

  if (path.endsWith("/slow")) {
    state.slowStarted += 1;
    let completed = false;
    res.setHeader("Content-Type", "text/html; charset=utf-8");
    res.statusCode = 200;
    res.flushHeaders();
    res.write("<div>partial");
    const finish = setTimeout(() => {
      completed = true;
      res.end(" popup</div>");
    }, 10000);
    res.on("close", () => {
      clearTimeout(finish);
      if (!completed) {
        state.slowAborted += 1;
      }
    });
    return;
  }

  res.statusCode = 404;
  res.end();
};
fixtureFactory.$inject = [];

config.plugins = config.plugins || [];
config.plugins.push({ "middleware:notiflyPopupFixture": ["factory", fixtureFactory] });
config.set({ middleware: ["notiflyPopupFixture"] });
