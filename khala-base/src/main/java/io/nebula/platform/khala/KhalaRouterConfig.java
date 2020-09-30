package io.nebula.platform.khala;

import android.app.Application;

/**
 * @author panxinghai
 * <p>
 * date : 2019-10-08 18:25
 */
public class KhalaRouterConfig {
    Application context;
    KhalaRouter.NavigateCallback navigateCallback;

    private KhalaRouterConfig(Builder builder) {
        context = builder.mContext;
        navigateCallback = builder.mNavigateCallback;
    }

    public static class Builder {
        private Application mContext;
        private KhalaRouter.NavigateCallback mNavigateCallback;

        public Builder(Application context) {
            mContext = context;
        }

        public Builder setNavigateCallback(KhalaRouter.NavigateCallback navigateCallback) {
            mNavigateCallback = navigateCallback;
            return this;
        }

        public KhalaRouterConfig build() {
            return new KhalaRouterConfig(this);
        }
    }
}
